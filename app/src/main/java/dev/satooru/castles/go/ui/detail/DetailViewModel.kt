package dev.satooru.castles.go.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.CastleTagEntity
import dev.satooru.castles.go.data.local.VisitEntity
import dev.satooru.castles.go.data.repository.CastleDetail
import dev.satooru.castles.go.data.repository.DetailResult
import dev.satooru.castles.go.ui.common.MdBlock
import dev.satooru.castles.go.ui.common.parseMarkdown
import dev.satooru.castles.go.ui.common.splitBySections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    /** [blocks]・[sections] は，表示用に別のスレッドで整形済みの解説と属性 */
    data class Loaded(
        val detail: CastleDetail,
        val fromCache: Boolean,
        /** 解説を見出しごとに分けたもの */
        val sections: List<List<MdBlock>>,
        val attributeSections: List<AttributeSection>,
    ) : DetailUiState
    data object Gone : DetailUiState
    data object Failed : DetailUiState
}

class DetailViewModel(private val castleId: String, container: AppContainer) : ViewModel() {
    private val repository = container.castleRepository

    /** 城のデータを Room から最初に受け取ったか．受け取る前に「端末にありません」を出さないため． */
    val castleResolved = MutableStateFlow(false)

    val castle: StateFlow<CastleEntity?> = repository.observeCastle(castleId)
        .onEach { castleResolved.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<CastleTagEntity>> = repository.observeTagsOf(castleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val location = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val visits = container.visitRepository

    val visit: StateFlow<VisitEntity?> = visits.observe(castleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markVisited() { viewModelScope.launch { visits.markVisited(castleId) } }

    fun unmarkVisited() { viewModelScope.launch { visits.unmark(castleId) } }

    fun updateVisit(visit: VisitEntity) { viewModelScope.launch { visits.update(visit) } }

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state

    init { load() }

    /** 詳細は開いたときのみ取得する．版が一致すれば API を呼ばない． */
    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val r = repository.loadDetail(castleId)) {
                is DetailResult.Success -> withContext(Dispatchers.Default) {
                    DetailUiState.Loaded(
                        detail = r.detail,
                        fromCache = r.fromCache,
                        sections = splitBySections(parseMarkdown(r.detail.body)),
                        attributeSections = AttributeFormatter.sections(r.detail.attributes),
                    )
                }
                DetailResult.Gone -> DetailUiState.Gone
                is DetailResult.Failure -> DetailUiState.Failed
            }
        }
    }
}
