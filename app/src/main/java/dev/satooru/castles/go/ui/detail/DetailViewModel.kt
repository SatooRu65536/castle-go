package dev.satooru.castles.go.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.CastleEntity
import dev.satooru.castles.go.data.local.CastleTagEntity
import dev.satooru.castles.go.data.repository.CastleDetail
import dev.satooru.castles.go.data.repository.DetailResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Loaded(val detail: CastleDetail, val fromCache: Boolean) : DetailUiState
    data object Gone : DetailUiState
    data object Failed : DetailUiState
}

class DetailViewModel(private val castleId: String, container: AppContainer) : ViewModel() {
    private val repository = container.castleRepository

    val castle: StateFlow<CastleEntity?> = repository.observeCastle(castleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<CastleTagEntity>> = repository.observeTagsOf(castleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val location = container.locationRepository.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state

    init { load() }

    /** 詳細は開いたときのみ取得する．版が一致すれば API を呼ばない． */
    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = when (val r = repository.loadDetail(castleId)) {
                is DetailResult.Success -> DetailUiState.Loaded(r.detail, r.fromCache)
                DetailResult.Gone -> DetailUiState.Gone
                is DetailResult.Failure -> DetailUiState.Failed
            }
        }
    }
}
