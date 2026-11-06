package com.cloudify.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudify.music.domain.model.Artist
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.domain.model.UserProfile
import com.cloudify.music.domain.repository.MusicRepository
import com.cloudify.music.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class QuickAccessItem(
    val id: String,
    val title: String,
    val artworkUrl: String,
    val isFavorites: Boolean = false,
    val tracks: List<Track> = emptyList()
)

data class ArtistRelease(
    val artist: Artist,
    val track: Track
)

data class HomeUiState(
    val greeting: String = "Добрый день",
    val user: UserProfile = UserProfile(),
    val quickAccess: List<QuickAccessItem> = emptyList(),
    val favoriteReleases: List<ArtistRelease> = emptyList(),
    val newReleaseArtist: Artist? = null,
    val newReleaseTrack: Track? = null,
    val waveTracks: List<Track> = emptyList(),
    val isWaveActive: Boolean = false,
    val popularArtists: List<Artist> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val popularForYou: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val activeFilterTab: String = "Все",
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val favoriteTrackIds: StateFlow<Set<String>> = musicRepository.favoriteIds
    val downloadedIds: StateFlow<Set<String>> = musicRepository.downloadedIds
    val favoriteArtistNames: StateFlow<Set<String>> = musicRepository.favoriteArtistNames

    init {
        determineGreeting()
        observeUser()
        observePlaylists()
        observeHistory()
        observeFavoriteArtists()
        loadInitialMusic()
    }

    private fun determineGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val text = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        _uiState.value = _uiState.value.copy(greeting = text)
    }

    private fun observeUser() {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { profile ->
                _uiState.value = _uiState.value.copy(user = profile)
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
                _uiState.value = _uiState.value.copy(recentTracks = list)
                updateQuickAccess(list)
            }
        }
    }

    private fun observeFavoriteArtists() {
        viewModelScope.launch {
            musicRepository.favoriteArtistNames.collect { favNames ->
                if (favNames.isNotEmpty()) {
                    loadFavoriteReleases(favNames.toList())
                } else {
                    _uiState.value = _uiState.value.copy(
                        favoriteReleases = emptyList(),
                        newReleaseArtist = null,
                        newReleaseTrack = null
                    )
                }
            }
        }
    }

    private suspend fun loadFavoriteReleases(favNames: List<String>) {
        val popularArtists = musicRepository.getPopularArtists()
        val releases = mutableListOf<ArtistRelease>()

        for (favArtistName in favNames.take(10)) {
            val artistObj = popularArtists.firstOrNull { it.name.equals(favArtistName, ignoreCase = true) }
                ?: Artist(
                    id = favArtistName.hashCode().toString(),
                    name = favArtistName,
                    artworkUrl = "",
                    genre = "Любимый исполнитель",
                    monthlyListeners = "В вашей медиатеке",
                    isFavorite = true
                )
            try {
                val tracks = musicRepository.search(favArtistName)
                val track = tracks.firstOrNull { it.artist.contains(favArtistName, ignoreCase = true) }
                    ?: tracks.firstOrNull()
                if (track != null) {
                    val updatedArtist = if (artistObj.artworkUrl.isEmpty()) {
                        artistObj.copy(artworkUrl = track.artworkUrl)
                    } else artistObj
                    releases.add(ArtistRelease(artist = updatedArtist, track = track))
                }
            } catch (_: Exception) { }
        }

        if (releases.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                favoriteReleases = releases,
                newReleaseArtist = releases.first().artist,
                newReleaseTrack = releases.first().track
            )
        } else {
            _uiState.value = _uiState.value.copy(
                favoriteReleases = emptyList(),
                newReleaseArtist = null,
                newReleaseTrack = null
            )
        }
    }

    fun setFilterTab(tab: String) {
        _uiState.value = _uiState.value.copy(activeFilterTab = tab)
    }

    private fun updateQuickAccess(history: List<Track>) {
        val quick = mutableListOf<QuickAccessItem>()
        // 1. Liked songs card
        quick.add(
            QuickAccessItem(
                id = "liked",
                title = "Любимые треки",
                artworkUrl = "",
                isFavorites = true
            )
        )
        // 2-8. Recent artists or tracks from history
        val seenArtists = mutableSetOf<String>()
        for (track in history) {
            if (seenArtists.add(track.artist)) {
                quick.add(
                    QuickAccessItem(
                        id = track.id,
                        title = track.artist,
                        artworkUrl = track.artworkUrl,
                        tracks = listOf(track)
                    )
                )
                if (quick.size >= 8) break
            }
        }
        // Fill remaining up to 8 with popular artists if history is short
        if (quick.size < 8) {
            val popular = musicRepository.getPopularArtists()
            for (artist in popular) {
                if (seenArtists.add(artist.name)) {
                    quick.add(
                        QuickAccessItem(
                            id = artist.id,
                            title = artist.name,
                            artworkUrl = artist.artworkUrl
                        )
                    )
                    if (quick.size >= 8) break
                }
            }
        }
        _uiState.value = _uiState.value.copy(quickAccess = quick)
    }

    fun loadInitialMusic() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val wave = musicRepository.getWaveTracks()
                val popular = musicRepository.getPopularForYou()
                val trending = musicRepository.getTrendingTracks()
                val artists = musicRepository.getPopularArtists()

                val favNames = musicRepository.favoriteArtistNames.value
                val releases = mutableListOf<ArtistRelease>()

                if (favNames.isNotEmpty()) {
                    for (favArtistName in favNames.take(10)) {
                        val artistObj = artists.firstOrNull { it.name.equals(favArtistName, ignoreCase = true) }
                            ?: Artist(id = favArtistName.hashCode().toString(), name = favArtistName, artworkUrl = "", genre = "Исполнитель", monthlyListeners = "", isFavorite = true)
                        try {
                            val tracks = musicRepository.search(favArtistName)
                            val track = tracks.firstOrNull { it.artist.contains(favArtistName, ignoreCase = true) } ?: tracks.firstOrNull()
                            if (track != null) {
                                val updatedArtist = if (artistObj.artworkUrl.isEmpty()) artistObj.copy(artworkUrl = track.artworkUrl) else artistObj
                                releases.add(ArtistRelease(artist = updatedArtist, track = track))
                            }
                        } catch (_: Exception) { }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    waveTracks = wave,
                    popularForYou = popular,
                    trendingTracks = trending,
                    popularArtists = artists,
                    favoriteReleases = releases,
                    newReleaseArtist = releases.firstOrNull()?.artist,
                    newReleaseTrack = releases.firstOrNull()?.track,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun setWaveActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isWaveActive = active)
    }
}
