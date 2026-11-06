package com.cloudify.music.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.domain.repository.MusicRepository
import com.cloudify.music.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab {
    FAVORITES, PLAYLISTS
}

enum class SortOrder {
    DATE_ADDED, TITLE, ARTIST
}

data class ImportProgressState(
    val isImporting: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentLine: String = "",
    val importedCount: Int = 0,
    val isFinished: Boolean = false,
    val error: String? = null
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val favoriteTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DATE_ADDED,
    val isCreatePlaylistDialogOpen: Boolean = false,
    val importState: ImportProgressState = ImportProgressState()
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val favoriteTrackIds: StateFlow<Set<String>> = musicRepository.favoriteIds
    val downloadedIds: StateFlow<Set<String>> = musicRepository.downloadedIds
    val isImportCompleted: Flow<Boolean> = userRepository.isImportCompleted()

    init {
        observeFavorites()
        observeDownloads()
        observePlaylists()
        observeHistory()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            musicRepository.getFavoriteTracks().collect { list ->
                _uiState.value = _uiState.value.copy(
                    favoriteTracks = sortTracks(list, _uiState.value.sortOrder)
                )
                // If the user already has tracks in favorites, automatically mark import completed
                if (list.size >= 10) {
                    userRepository.setImportCompleted(true)
                }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            musicRepository.getDownloadedTracks().collect { list ->
                _uiState.value = _uiState.value.copy(
                    downloadedTracks = sortTracks(list, _uiState.value.sortOrder)
                )
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            musicRepository.getPlaylists().collect { list ->
                _uiState.value = _uiState.value.copy(playlists = list)
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            musicRepository.getHistory().collect { list ->
                _uiState.value = _uiState.value.copy(historyTracks = list)
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(
            sortOrder = order,
            favoriteTracks = sortTracks(_uiState.value.favoriteTracks, order),
            downloadedTracks = sortTracks(_uiState.value.downloadedTracks, order)
        )
    }

    fun openCreatePlaylistDialog(open: Boolean) {
        _uiState.value = _uiState.value.copy(isCreatePlaylistDialogOpen = open)
    }

    fun createPlaylist(name: String, artworkUrl: String = "") {
        viewModelScope.launch {
            musicRepository.createPlaylist(name, artworkUrl)
            openCreatePlaylistDialog(false)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun playPlaylist(playlist: Playlist, onReady: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val trackIds = musicRepository.getPlaylistTrackIds(playlist.id).toSet()
            val matchedTracks = _uiState.value.favoriteTracks.filter { trackIds.contains(it.id) }
            if (matchedTracks.isNotEmpty()) {
                onReady(matchedTracks)
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun dismissImportDialog() {
        _uiState.value = _uiState.value.copy(
            importState = ImportProgressState(isImporting = false)
        )
    }

    fun importTracksFromText(rawText: String) {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .map { line ->
                line.replace(Regex("""^\s*\[?\d+[.)\]\-:]*\s*"""), "").trim()
            }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                importState = ImportProgressState(
                    isImporting = true,
                    isFinished = true,
                    error = "В файле не найдено строк с треками"
                )
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                importState = ImportProgressState(
                    isImporting = true,
                    current = 0,
                    total = lines.size,
                    currentLine = lines.first(),
                    importedCount = 0
                )
            )

            var imported = 0
            val baseTime = System.currentTimeMillis()

            for ((index, line) in lines.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    importState = _uiState.value.importState.copy(
                        current = index + 1,
                        currentLine = line
                    )
                )

                try {
                    val searchResults = musicRepository.search(line)
                    if (searchResults.isNotEmpty()) {
                        val track = searchResults.first()
                        val timestamp = baseTime + (lines.size - index) * 1000L
                        musicRepository.addFavorite(track, timestamp)
                        imported++
                    }
                } catch (e: Exception) {
                    // Continue with next tracks
                }
            }

            _uiState.value = _uiState.value.copy(
                importState = _uiState.value.importState.copy(
                    isFinished = true,
                    importedCount = imported
                )
            )
            userRepository.setImportCompleted(true)
        }
    }

    private fun sortTracks(tracks: List<Track>, order: SortOrder): List<Track> {
        return when (order) {
            SortOrder.DATE_ADDED -> tracks
            SortOrder.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
        }
    }
}
