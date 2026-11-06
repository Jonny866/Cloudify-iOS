package com.cloudify.music.core.util

import android.content.Context
import com.cloudify.music.core.database.*
import com.cloudify.music.core.network.*
import com.cloudify.music.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SyncRestoreResult(
    val success: Boolean,
    val restoredFavorites: Int = 0,
    val restoredPlaylists: Int = 0,
    val restoredArtists: Int = 0,
    val serverUserName: String? = null,
    val serverAvatarUrl: String? = null,
    val message: String? = null
)

class CloudSyncManager(private val context: Context) {

    private val api = CloudifyApi.create()
    private val db = AppDatabase.getInstance(context)
    private val dao = db.musicDao()

    /**
     * Downloads user favorites, playlists, and artists from the backend server
     * using the Google Account user ID or email, and restores them into local Room database.
     */
    suspend fun restoreFromServer(userId: String?, email: String?): SyncRestoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUserId = userId?.trim()?.ifBlank { null }
                val cleanEmail = email?.trim()?.lowercase()?.ifBlank { null }

                if (cleanUserId == null && cleanEmail == null) {
                    return@withContext SyncRestoreResult(success = false, message = "Не указан email или ID пользователя")
                }

                android.util.Log.d("CloudSync", "Restoring from server for email=$cleanEmail, userId=$cleanUserId")
                val res = api.getUserData(userId = cleanUserId, email = cleanEmail)

                if (res.status != "ok") {
                    return@withContext SyncRestoreResult(success = false, message = "Резервная копия не найдена на сервере")
                }

                var favCount = 0
                var plCount = 0
                var artistCount = 0

                // 1. Restore Favorites
                for (f in res.favorites) {
                    val entity = FavoriteTrackEntity(
                        id = f.id,
                        title = f.title,
                        artist = f.artist,
                        artworkUrl = f.artworkUrl,
                        streamUrl = f.streamUrl,
                        durationMs = f.durationMs,
                        genre = f.genre,
                        lyrics = f.lyrics,
                        addedAt = if (f.addedAt > 0) f.addedAt else System.currentTimeMillis()
                    )
                    dao.insertFavorite(entity)
                    favCount++
                }

                // 2. Restore Playlists
                for (pl in res.playlists) {
                    dao.insertPlaylist(
                        PlaylistEntity(
                            id = pl.id,
                            name = pl.name,
                            artworkUrl = pl.artworkUrl,
                            trackCount = pl.trackIds.size,
                            createdAt = if (pl.createdAt > 0) pl.createdAt else System.currentTimeMillis()
                        )
                    )
                    plCount++

                    for (tId in pl.trackIds) {
                        dao.insertPlaylistTrack(
                            PlaylistTrackCrossRef(
                                playlistId = pl.id,
                                trackId = tId
                            )
                        )
                    }
                }

                // 3. Restore Artists
                for (a in res.favoriteArtists) {
                    dao.insertFavoriteArtist(
                        FavoriteArtistEntity(
                            name = a.name,
                            artworkUrl = a.artworkUrl,
                            genre = a.genre,
                            monthlyListeners = a.monthlyListeners,
                            addedAt = if (a.addedAt > 0) a.addedAt else System.currentTimeMillis()
                        )
                    )
                    artistCount++
                }

                android.util.Log.d("CloudSync", "Restored successfully: $favCount favorites, $plCount playlists, $artistCount artists")

                SyncRestoreResult(
                    success = true,
                    restoredFavorites = favCount,
                    restoredPlaylists = plCount,
                    restoredArtists = artistCount,
                    serverUserName = res.user?.name,
                    serverAvatarUrl = res.user?.avatarUrl
                )
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("CloudSync", "Restore error: ${e.message}", e)
                SyncRestoreResult(success = false, message = e.localizedMessage ?: "Ошибка связи с сервером")
            }
        }
    }

    /**
     * Uploads all current local favorites, playlists, and artists to the backend server.
     */
    suspend fun syncToServer(profile: UserProfile): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (profile.id.isBlank() && profile.email.isBlank()) return@withContext false

                val favorites = dao.getAllFavorites().first()
                val playlists = dao.getAllPlaylists().first()
                val artists = dao.getAllFavoriteArtists().first()

                val favDtos = favorites.map {
                    FavoriteSyncDto(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        artworkUrl = it.artworkUrl,
                        streamUrl = it.streamUrl,
                        durationMs = it.durationMs,
                        genre = it.genre,
                        lyrics = it.lyrics,
                        addedAt = it.addedAt
                    )
                }

                val plDtos = playlists.map { pl ->
                    val tIds = dao.getPlaylistTrackIds(pl.id)
                    PlaylistSyncDto(
                        id = pl.id,
                        name = pl.name,
                        artworkUrl = pl.artworkUrl,
                        createdAt = pl.createdAt,
                        trackIds = tIds
                    )
                }

                val artistDtos = artists.map { a ->
                    ArtistSyncDto(
                        name = a.name,
                        artworkUrl = a.artworkUrl,
                        genre = a.genre,
                        monthlyListeners = a.monthlyListeners,
                        addedAt = a.addedAt
                    )
                }

                val payload = SyncPayloadDto(
                    user = UserSyncDto(
                        id = profile.id,
                        name = profile.name,
                        email = profile.email,
                        avatarUrl = profile.avatarUrl
                    ),
                    favorites = favDtos,
                    playlists = plDtos,
                    favoriteArtists = artistDtos
                )

                val res = api.syncUserData(payload)
                res.status == "ok"
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
