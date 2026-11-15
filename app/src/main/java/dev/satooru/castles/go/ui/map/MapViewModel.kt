package dev.satooru.castles.go.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.NearbyCastle
import dev.satooru.castles.go.data.repository.SyncResult
import dev.satooru.castles.go.settings.NearbyMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

/** 地図の表示範囲と，ズームに応じた規模の下限． */
data class MapViewport(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val minScale: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.castleRepository

    val syncing = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)

    init {
        // 前回の確認から一定時間（6 時間）を超えていれば，起動時に同期する．
        viewModelScope.launch {
            syncing.value = true
            try {
                syncError.value = (container.syncRepository.syncIfStale() as? SyncResult.Failure)?.summary
            } finally {
                syncing.value = false
            }
        }
    }

    /** 現在地に近い城（上位 3 件）．100 m 以上動いたとき，設定・城の件数が変わったときに再計算する． */
    val nearby: StateFlow<List<NearbyCastle>> = combine(
        container.locationRepository.location.distinctUntilChanged { a, b ->
            a == b || (a != null && b != null && DistanceCalculator.meters(a, b) < 100)
        },
        container.settingsRepository.settings,
        repository.observeCount(),
    ) { here, settings, _ -> here to settings }
        .flatMapLatest { (here, settings) ->
            flow {
                emit(
                    if (here == null) emptyList()
                    else repository.nearby(
                        origin = here,
                        filter = CastleFilter(),
                        limit = NEARBY_COUNT,
                        radiusKm = if (settings.nearbyMode == NearbyMode.RADIUS) settings.effectiveRadiusKm else null,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val location: StateFlow<GeoPoint?> = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val viewport = MutableStateFlow<MapViewport?>(null)
    val selected = MutableStateFlow<CastleEntity?>(null)

    /** 表示範囲の城のみ．規模で間引き，件数にも上限を設ける． */
    val castles: StateFlow<List<CastleEntity>> = viewport.filterNotNull()
        .flatMapLatest { v ->
            repository.observeInBounds(v.minLat, v.maxLat, v.minLng, v.maxLng, v.minScale, MAX_MARKERS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onViewportChanged(v: MapViewport) { viewport.value = v }

    fun select(castle: CastleEntity?) { selected.value = castle }

    companion object {
        const val MAX_MARKERS = 300
        const val NEARBY_COUNT = 3

        /** ズームが小さい（広域）ほど，規模の大きい城のみ描く． */
        fun minScaleFor(zoom: Float): Int = when {
            zoom < 6f -> 6
            zoom < 8f -> 5
            zoom < 9.5f -> 4
            zoom < 11f -> 3
            zoom < 12.5f -> 2
            else -> 1
        }
    }
}
