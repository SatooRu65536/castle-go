package dev.satooru.castles.go.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.satooru.castles.go.AppContainer
import dev.satooru.castles.go.data.local.MeijoProgressRow
import dev.satooru.castles.go.data.local.VisitedCastleRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class VisitsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.visitRepository

    val visits: StateFlow<List<VisitedCastleRow>> = repository.observeVisitedCastles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<List<MeijoProgressRow>> = repository.observeMeijoProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
