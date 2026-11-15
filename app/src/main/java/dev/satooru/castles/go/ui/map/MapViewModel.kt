package dev.satooru.castles.go.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.NearbyCastle
import dev.satooru.castles.go.data.repository.SyncResult
import dev.satooru.castles.go.settings.NearbyMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

/** 地図の表示範囲と，ズームに応じた規模の下限． */
data class MapViewport(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
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

    /** 地図の中心（カメラの位置）．左上の最寄りの城の基準．まだ地図が動いていないときは NULL． */
    private val mapCenter = MutableStateFlow<GeoPoint?>(null)

    fun onCenterChanged(center: GeoPoint) { mapCenter.value = center }

    /** 左上の最寄りの城の基準．地図の中心．地図がまだ動いていない間は現在地． */
    private val nearbyOrigin: Flow<GeoPoint?> = combine(mapCenter, container.locationRepository.location) { center, here ->
        center ?: here
    }.distinctUntilChanged { a, b ->
        // 50 m 未満の動きでは再計算しない．
        a == b || (a != null && b != null && DistanceCalculator.meters(a, b) < 50)
    }

    /** 地図の中心に近い城（上位 3 件）．中心が 50 m 以上動いたとき，設定・城の件数が変わったときに再計算する． */
    val nearby: StateFlow<List<NearbyCastle>> = combine(
        nearbyOrigin,
        container.settingsRepository.settings,
        repository.observeCount(),
    ) { origin, settings, _ -> origin to settings }
        .flatMapLatest { (origin, settings) ->
            flow {
                emit(
                    if (origin == null) emptyList()
                    else repository.nearby(
                        origin = origin,
                        // 表示する規模の設定は，左上の最寄りの城にも使う（地図に出ていない城を出さないため）．
                        filter = CastleFilter(scaleMask = settings.visibleScaleMask),
                        limit = NEARBY_COUNT,
                        // スマホは近い順に 3 件．件数 N・半径の設定は Android Auto のみに使う．
                        radiusKm = null,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val location: StateFlow<GeoPoint?> = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val viewport = MutableStateFlow<MapViewport?>(null)
    val selected = MutableStateFlow<CastleEntity?>(null)

    // 詳細画面などへ遷移すると地図の画面は破棄されるため，戻ったときに復元できるよう ViewModel に持つ．
    /** 地図を離れたときのカメラの位置．NULL なら初期位置 */
    var savedCamera: CameraPosition? = null
    /** 最初の現在地への移動を済ませたか */
    var movedToUser = false
    /** 現在地を中心にして追従している間は true */
    var following = false

    /** 表示範囲の城．設定で表示を選んだ規模のみ（件数の上限や，ズームによる間引きはしない）． */
    val castles: StateFlow<List<CastleEntity>> = combine(
        viewport.filterNotNull(),
        container.settingsRepository.settings.map { it.visibleScaleMask }.distinctUntilChanged(),
    ) { v, mask -> v to mask }
        .flatMapLatest { (v, mask) ->
            // スマホでは画面内の城をすべて出す（件数の上限・規模の間引きはしない）．まとめて表示するため，重なっても読みやすい．
            repository.observeInBounds(v.minLat, v.maxLat, v.minLng, v.maxLng, 1, mask, Int.MAX_VALUE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onViewportChanged(v: MapViewport) { viewport.value = v }

    fun select(castle: CastleEntity?) { selected.value = castle }

    companion object {
        const val NEARBY_COUNT = 3

        /** すべての城に城名を出し始めるズーム．これより縮小した地図では規模 4 以上と選択中のみ． */
        const val ALL_LABELS_MIN_ZOOM = 14f
        const val LABELED_MIN_SCALE = 4
    }
}
