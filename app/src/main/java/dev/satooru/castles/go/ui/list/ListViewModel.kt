package dev.satooru.castles.go.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.CastleTagEntity
import dev.satooru.castles.go.data.repository.CastleFilter
import dev.satooru.castles.go.data.repository.DistanceCalculator
import dev.satooru.castles.go.data.repository.GeoPoint
import dev.satooru.castles.go.data.repository.SyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ListViewModel(private val container: AppContainer) : ViewModel() {
    private val castles = container.castleRepository
    private val sync = container.syncRepository

    val filter = MutableStateFlow(CastleFilter())

    /** 現在地．一覧の並べ替えは，300 m 以上動いたときだけ作り直す（読み込みの巻き戻りを避ける）． */
    val location: StateFlow<GeoPoint?> = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 画面が前面にある間のみ，画面側が現在地を渡す（背景で位置情報の購読を残さないため）． */
    private val origin = MutableStateFlow<GeoPoint?>(null)

    fun setOrigin(point: GeoPoint?) { origin.value = point }

    private val sortOrigin: Flow<GeoPoint?> = origin
        .distinctUntilChanged { a, b ->
            a == b || (a != null && b != null && DistanceCalculator.meters(a, b) < 300)
        }

    val paged: Flow<PagingData<CastleEntity>> = combine(filter.debounce(250), sortOrigin) { f, o -> f to o }
        .flatMapLatest { (f, o) -> castles.pagedCastles(f, o) }
        .cachedIn(viewModelScope)

    val tags: StateFlow<List<CastleTagEntity>> = castles.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = castles.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val syncing = MutableStateFlow(false)
    val syncMessage = MutableStateFlow<String?>(null)

    init {
        // 前回の確認から一定時間（6 時間）を超えていれば，起動時に同期する．
        viewModelScope.launch { runSync(force = false) }
    }

    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setFilter(f: CastleFilter) = filter.update { f.copy(query = it.query) }

    fun refresh() = viewModelScope.launch { runSync(force = true) }

    private suspend fun runSync(force: Boolean) {
        syncing.value = true
        val result = try {
            if (force) sync.sync() else sync.syncIfStale()
        } finally {
            syncing.value = false
        }
        syncMessage.value = when (result) {
            is SyncResult.Success -> if (force) "更新しました（${result.updatedCount} 件）" else null
            SyncResult.Skipped -> null
            is SyncResult.Failure -> "同期に失敗しました．保存済みのデータを表示しています"
        }
    }

    fun consumeSyncMessage() { syncMessage.value = null }
}
