package com.cloudify.music.domain.repository

import com.cloudify.music.domain.model.Artist
import com.cloudify.music.domain.model.ArtistDetail
import com.cloudify.music.domain.model.MonthlyStats
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MusicRepository {
    val favoriteIds: StateFlow<Set<String>>
    val downloadedIds: StateFlow<Set<String>>
    val favoriteArtistNames: StateFlow<Set<String>>

    suspend fun search(query: String): List<Track>
    suspend fun getWaveTracks(): List<Track>
    suspend fun getTrendingTracks(): List<Track>
    suspend fun getPopularForYou(): List<Track>
    fun getPopularArtists(): List<Artist>
    
    // Artist Detail & SoundCloud Releases
    suspend fun getArtistDetail(artistName: String): ArtistDetail
    suspend fun toggleFavoriteArtist(artist: Artist)
    fun getFavoriteArtists(): Flow<List<Artist>>
    
    // Favorites
    fun getFavoriteTracks(): Flow<List<Track>>
    suspend fun toggleFavorite(track: Track)
    suspend fun addFavorite(track: Track, customAddedAt: Long? = null)
    
    // Offline Downloads
    fun getDownloadedTracks(): Flow<List<Track>>
    suspend fun saveDownloadedTrack(track: Track, localFilePath: String, localArtworkPath: String? = null)
    suspend fun removeDownloadedTrack(trackId: String)
    
    // Playlists
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String, artworkUrl: String = "")
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, track: Track)
    suspend fun getPlaylistTrackIds(playlistId: String): List<String>
    
    // History & Real Listening Time
    fun getHistory(): Flow<List<Track>>
    suspend fun recordPlayed(track: Track)
    suspend fun addListenedSeconds(seconds: Long)
    suspend fun getTotalListenedSeconds(): Long
    
    // Search history
    fun getRecentSearches(): Flow<List<String>>
    suspend fun saveSearch(query: String)
    suspend fun deleteSearch(query: String)

    // Monthly stats
    suspend fun getMonthlyStats(): MonthlyStats
}
