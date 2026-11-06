package com.cloudify.music.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // Favorites
    @Query("SELECT * FROM favorite_tracks ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteTrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE id = :trackId)")
    fun isFavorite(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(track: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE id = :trackId")
    suspend fun deleteFavorite(trackId: String)

    // Downloaded Tracks (Offline playback)
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :trackId)")
    fun isTrackDownloaded(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteDownloadedTrack(trackId: String)

    // Favorite Artists
    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    fun getAllFavoriteArtists(): Flow<List<FavoriteArtistEntity>>

    @Query("SELECT * FROM favorite_artists WHERE name = :name LIMIT 1")
    suspend fun getFavoriteArtist(name: String): FavoriteArtistEntity?

    @Query("SELECT * FROM favorite_tracks WHERE artist = :artist ORDER BY addedAt DESC LIMIT 1")
    suspend fun getTrackByArtist(artist: String): FavoriteTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_artists WHERE name = :name)")
    fun isArtistFavorite(name: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteArtist(artist: FavoriteArtistEntity)

    @Query("DELETE FROM favorite_artists WHERE name = :name")
    suspend fun deleteFavoriteArtist(name: String)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("UPDATE playlists SET name = :newName, artworkUrl = :newArtwork WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: String, newName: String, newArtwork: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getPlaylistTrackIds(playlistId: String): List<String>

    // History
    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC")
    suspend fun getAllHistoryList(): List<HistoryEntity>

    @Query("SELECT COUNT(*) FROM listening_history")
    suspend fun getTotalPlaysCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()

    // Search History
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(search: SearchQueryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)
}
