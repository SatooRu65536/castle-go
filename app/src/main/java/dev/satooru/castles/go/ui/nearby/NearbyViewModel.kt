package dev.satooru.castles.go.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.NearbyCastle
import dev.satooru.castles.go.settings.AppSettings
import dev.satooru.castles.go.settings.NearbyMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** 周辺の表示の追加の絞り込み．設定の「周辺の表示」と併用できる． */
data class NearbyFilter(
    val meijoSelection: String? = null,
    val unvisitedOnly: Boolean = false,
    val minScale: Int = 1,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModel(private val container: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(NearbyFilter())

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings

    val location: StateFlow<GeoPoint?> = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 100 m 以上動いたとき，設定・絞り込みが変わったときに再計算する． */
    val items: StateFlow<List<NearbyCastle>> = combine(
        container.locationRepository.location.distinctUntilChanged { a, b ->
            a == b || (a != null && b != null && DistanceCalculator.meters(a, b) < 100)
        },
        container.settingsRepository.settings,
        filter,
    ) { here, s, f -> Triple(here, s, f) }
        .flatMapLatest { (here, s, f) ->
            flow {
                if (here == null) {
                    emit(emptyList())
                } else {
                    emit(
                        container.castleRepository.nearby(
                            origin = here,
                            filter = CastleFilter(
                                minScale = f.minScale,
                                meijoSelection = f.meijoSelection,
                                unvisitedOnly = f.unvisitedOnly,
                            ),
                            limit = if (s.nearbyMode == NearbyMode.TOP_N) s.topN else MAX_ROWS,
                            radiusKm = if (s.nearbyMode == NearbyMode.RADIUS) s.effectiveRadiusKm else null,
                        ),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val MAX_ROWS = 200
    }
}
