package com.cloudify.music.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudify.music.domain.model.MonthlyStats
import com.cloudify.music.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val stats: MonthlyStats = MonthlyStats(),
    val isLoading: Boolean = false
)

class StatsViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val data = musicRepository.getMonthlyStats()
            _uiState.value = _uiState.value.copy(stats = data, isLoading = false)
        }
    }
}
