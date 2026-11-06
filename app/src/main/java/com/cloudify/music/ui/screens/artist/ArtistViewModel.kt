package com.cloudify.music.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudify.music.domain.model.ArtistDetail
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailState(
    val album: Playlist,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

data class ArtistUiState(
    val artistName: String = "",
    val detail: ArtistDetail? = null,
    val isLoading: Boolean = true
)

class ArtistViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<AlbumDetailState?>(null)
    val selectedAlbum: StateFlow<AlbumDetailState?> = _selectedAlbum.asStateFlow()

    val favoriteTrackIds: StateFlow<Set<String>> = musicRepository.favoriteIds
    val downloadedIds: StateFlow<Set<String>> = musicRepository.downloadedIds
    val favoriteArtistNames: StateFlow<Set<String>> = musicRepository.favoriteArtistNames

    fun loadArtist(artistName: String) {
        _uiState.value = _uiState.value.copy(artistName = artistName, isLoading = true)
        viewModelScope.launch {
            try {
                val detail = musicRepository.getArtistDetail(artistName)
                _uiState.value = _uiState.value.copy(detail = detail, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun openAlbum(album: Playlist) {
        _selectedAlbum.value = AlbumDetailState(album = album, isLoading = true)
        viewModelScope.launch {
            try {
                val query = "${_uiState.value.artistName} ${album.name}"
                val results = musicRepository.search(query)
                val tracks = if (results.isNotEmpty()) results else _uiState.value.detail?.allTracks?.take(album.trackCount) ?: emptyList()
                _selectedAlbum.value = AlbumDetailState(album = album, tracks = tracks, isLoading = false)
            } catch (e: Exception) {
                val fallback = _uiState.value.detail?.allTracks?.take(album.trackCount) ?: emptyList()
                _selectedAlbum.value = AlbumDetailState(album = album, tracks = fallback, isLoading = false)
            }
        }
    }

    fun closeAlbum() {
        _selectedAlbum.value = null
    }

    fun playAlbumDirectly(album: Playlist, onReady: (Track, List<Track>) -> Unit) {
        viewModelScope.launch {
            try {
                val query = "${_uiState.value.artistName} ${album.name}"
                val results = musicRepository.search(query)
                val tracks = if (results.isNotEmpty()) results else _uiState.value.detail?.allTracks?.take(album.trackCount) ?: emptyList()
                if (tracks.isNotEmpty()) {
                    onReady(tracks.first(), tracks)
                }
            } catch (_: Exception) {
                val fallback = _uiState.value.detail?.allTracks?.take(album.trackCount) ?: emptyList()
                if (fallback.isNotEmpty()) {
                    onReady(fallback.first(), fallback)
                }
            }
        }
    }

    fun toggleArtistFavorite() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            musicRepository.toggleFavoriteArtist(detail.artist)
            val isNowFav = favoriteArtistNames.value.contains(detail.artist.name)
            _uiState.value = _uiState.value.copy(
                detail = detail.copy(isFavorite = isNowFav)
            )
        }
    }

    fun toggleTrackFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }
}
