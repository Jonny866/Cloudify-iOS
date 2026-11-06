package com.cloudify.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudify.music.domain.model.Artist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchFilter {
    ALL, TRACKS, ARTISTS, ALBUMS
}

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val filteredResults: List<Track> = emptyList(),
    val popularArtists: List<Artist> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val activeFilter: SearchFilter = SearchFilter.ALL,
    val popularGenres: List<String> = listOf("Phonk", "Хип-Хоп", "Электроника", "Рок", "Lofi", "Дрифт", "Трэп", "Поп"),
    val isSearching: Boolean = false
)

class SearchViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteTrackIds: StateFlow<Set<String>> = musicRepository.favoriteIds
    val downloadedIds: StateFlow<Set<String>> = musicRepository.downloadedIds

    private var searchJob: Job? = null

    init {
        observeSearchHistory()
        loadArtists()
    }

    private fun loadArtists() {
        _uiState.value = _uiState.value.copy(
            popularArtists = musicRepository.getPopularArtists()
        )
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            musicRepository.getRecentSearches().collect { history ->
                _uiState.value = _uiState.value.copy(searchHistory = history)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), filteredResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(400L)
            performSearch(newQuery)
        }
    }

    fun performSearch(queryToSearch: String) {
        val q = queryToSearch.trim()
        if (q.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(query = q, isSearching = true)
            musicRepository.saveSearch(q)
            try {
                val found = musicRepository.search(q)
                _uiState.value = _uiState.value.copy(
                    results = found,
                    filteredResults = applyFilter(found, _uiState.value.activeFilter),
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun setFilter(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(
            activeFilter = filter,
            filteredResults = applyFilter(_uiState.value.results, filter)
        )
    }

    fun deleteHistoryItem(item: String) {
        viewModelScope.launch {
            musicRepository.deleteSearch(item)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    private fun applyFilter(list: List<Track>, filter: SearchFilter): List<Track> {
        return when (filter) {
            SearchFilter.ALL -> list
            SearchFilter.TRACKS -> list
            SearchFilter.ARTISTS -> list.sortedBy { it.artist }
            SearchFilter.ALBUMS -> list
        }
    }
}
