package com.cloudify.music.core.util

import android.content.Context
import android.os.Environment
import com.cloudify.music.core.database.*
import com.cloudify.music.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class BackupRestoreResult(
    val success: Boolean,
    val restoredFavorites: Int = 0,
    val restoredPlaylists: Int = 0,
    val restoredArtists: Int = 0,
    val errorMessage: String? = null
)

class DatabaseBackupManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.musicDao()

    /**
     * Serializes all user playlists, liked tracks, favorite artists, and settings into JSON
     * and streams it to the provided OutputStream (e.g. SAF Document on Disk D, USB, or local drive).
     */
    suspend fun exportBackupJson(outputStream: OutputStream, userProfile: UserProfile? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject()
                root.put("app", "Cloudify")
                root.put("version", "7.4")
                root.put("exportedAt", System.currentTimeMillis())
                root.put("dateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                // 1. User Profile
                if (userProfile != null) {
                    val userObj = JSONObject()
                    userObj.put("id", userProfile.id)
                    userObj.put("name", userProfile.name)
                    userObj.put("email", userProfile.email)
                    userObj.put("avatarUrl", userProfile.avatarUrl)
                    root.put("user", userObj)
                }

                // 2. Favorite Tracks
                val favorites = dao.getAllFavorites().first()
                val favArray = JSONArray()
                for (fav in favorites) {
                    val item = JSONObject()
                    item.put("id", fav.id)
                    item.put("title", fav.title)
                    item.put("artist", fav.artist)
                    item.put("artworkUrl", fav.artworkUrl)
                    item.put("streamUrl", fav.streamUrl)
                    item.put("durationMs", fav.durationMs)
                    item.put("genre", fav.genre)
                    item.put("lyrics", fav.lyrics)
                    item.put("addedAt", fav.addedAt)
                    favArray.put(item)
                }
                root.put("favorites", favArray)

                // 3. Playlists and their Tracks
                val playlists = dao.getAllPlaylists().first()
                val playlistArray = JSONArray()
                for (pl in playlists) {
                    val plObj = JSONObject()
                    plObj.put("id", pl.id)
                    plObj.put("name", pl.name)
                    plObj.put("artworkUrl", pl.artworkUrl)
                    plObj.put("trackCount", pl.trackCount)
                    plObj.put("createdAt", pl.createdAt)

                    val trackIds = dao.getPlaylistTrackIds(pl.id)
                    val tArray = JSONArray()
                    for (tId in trackIds) {
                        tArray.put(tId)
                    }
                    plObj.put("trackIds", tArray)
                    playlistArray.put(plObj)
                }
                root.put("playlists", playlistArray)

                // 4. Favorite Artists
                val artists = dao.getAllFavoriteArtists().first()
                val artistArray = JSONArray()
                for (a in artists) {
                    val aObj = JSONObject()
                    aObj.put("name", a.name)
                    aObj.put("artworkUrl", a.artworkUrl)
                    aObj.put("genre", a.genre)
                    aObj.put("monthlyListeners", a.monthlyListeners)
                    aObj.put("addedAt", a.addedAt)
                    artistArray.put(aObj)
                }
                root.put("favoriteArtists", artistArray)

                // Write with nice 2-space indentation
                val jsonString = root.toString(2)
                outputStream.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Reads JSON from InputStream and merges all favorites, playlists, and artists into Room Database.
     */
    suspend fun importBackupJson(inputStream: InputStream): BackupRestoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(jsonString)

                var restoredFavs = 0
                var restoredPlaylists = 0
                var restoredArtists = 0

                // 1. Restore Favorites
                if (root.has("favorites")) {
                    val favArray = root.getJSONArray("favorites")
                    for (i in 0 until favArray.length()) {
                        val item = favArray.getJSONObject(i)
                        val entity = FavoriteTrackEntity(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            artworkUrl = item.optString("artworkUrl"),
                            streamUrl = item.optString("streamUrl"),
                            durationMs = item.optLong("durationMs", 180000L),
                            genre = item.optString("genre", "SoundCloud"),
                            lyrics = item.optString("lyrics", ""),
                            addedAt = item.optLong("addedAt", System.currentTimeMillis())
                        )
                        if (entity.id.isNotBlank() && entity.title.isNotBlank()) {
                            dao.insertFavorite(entity)
                            restoredFavs++
                        }
                    }
                }

                // 2. Restore Playlists
                if (root.has("playlists")) {
                    val playlistArray = root.getJSONArray("playlists")
                    for (i in 0 until playlistArray.length()) {
                        val plObj = playlistArray.getJSONObject(i)
                        val plId = plObj.optString("id")
                        val plName = plObj.optString("name")
                        val plArtwork = plObj.optString("artworkUrl")
                        val plCreatedAt = plObj.optLong("createdAt", System.currentTimeMillis())

                        if (plId.isNotBlank() && plName.isNotBlank()) {
                            dao.insertPlaylist(
                                PlaylistEntity(
                                    id = plId,
                                    name = plName,
                                    artworkUrl = plArtwork,
                                    createdAt = plCreatedAt
                                )
                            )
                            restoredPlaylists++

                            if (plObj.has("trackIds")) {
                                val tArray = plObj.getJSONArray("trackIds")
                                for (j in 0 until tArray.length()) {
                                    val trackId = tArray.getString(j)
                                    dao.insertPlaylistTrack(
                                        PlaylistTrackCrossRef(
                                            playlistId = plId,
                                            trackId = trackId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Restore Favorite Artists
                if (root.has("favoriteArtists")) {
                    val artistArray = root.getJSONArray("favoriteArtists")
                    for (i in 0 until artistArray.length()) {
                        val aObj = artistArray.getJSONObject(i)
                        val aName = aObj.optString("name")
                        if (aName.isNotBlank()) {
                            dao.insertFavoriteArtist(
                                FavoriteArtistEntity(
                                    name = aName,
                                    artworkUrl = aObj.optString("artworkUrl"),
                                    genre = aObj.optString("genre", "Исполнитель"),
                                    monthlyListeners = aObj.optString("monthlyListeners", "850 тыс. слушателей"),
                                    addedAt = aObj.optLong("addedAt", System.currentTimeMillis())
                                )
                            )
                            restoredArtists++
                        }
                    }
                }

                BackupRestoreResult(
                    success = true,
                    restoredFavorites = restoredFavs,
                    restoredPlaylists = restoredPlaylists,
                    restoredArtists = restoredArtists
                )
            } catch (e: Exception) {
                e.printStackTrace()
                BackupRestoreResult(success = false, errorMessage = e.message ?: "Ошибка чтения файла бэкапа")
            }
        }
    }

    /**
     * Automatically saves a fresh backup JSON copy to the public Downloads folder:
     * /sdcard/Download/Cloudify_Backups/cloudify_backup_latest.json
     * so it can be easily copied to PC Drive D via USB or Windows Explorer.
     */
    suspend fun autoBackupToDownloads(userProfile: UserProfile? = null): File? {
        return withContext(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupDir = File(downloadsDir, "Cloudify_Backups").apply { mkdirs() }
                val targetFile = File(backupDir, "cloudify_backup_latest.json")
                val fos = FileOutputStream(targetFile)
                val ok = exportBackupJson(fos, userProfile)
                if (ok) targetFile else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Copies the raw SQLite cloudify_music.db to an accessible file in app external files directory.
     */
    fun exportRawDatabaseFile(): File? {
        return try {
            val dbFile = context.getDatabasePath("cloudify_music.db")
            if (!dbFile.exists()) return null

            val exportDir = context.getExternalFilesDir("database_export") ?: return null
            exportDir.mkdirs()
            val targetFile = File(exportDir, "cloudify_music_disk_D_backup.db")
            dbFile.copyTo(targetFile, overwrite = true)
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
