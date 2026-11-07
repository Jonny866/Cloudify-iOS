package com.cloudify.music.domain.model

import android.content.Context
import android.os.Environment
import java.io.File

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long = 180000L,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val localArtworkPath: String? = null,
    val genre: String = "Phonk",
    val lyrics: String = "Текст трека доступен в плеере...",
    val videoUrl: String? = null,
    val playsCount: String = "2,419,050"
) {
    fun getDisplayArtwork(context: Context? = null): Any {
        if (!localArtworkPath.isNullOrBlank()) {
            val file = File(localArtworkPath)
            if (file.exists() && file.length() > 200) {
                return file
            }
        }
        if (context != null) {
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "downloads")
            val safeId = id.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val artFile = File(musicDir, "cloudify_art_${safeId}.jpg")
            if (artFile.exists() && artFile.length() > 200) {
                return artFile
            }
        }
        return artworkUrl
    }
}

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String,
    val genre: String = "Исполнитель",
    val monthlyListeners: String = "839.6 тыс. слушателей",
    val isFavorite: Boolean = false
)

data class ArtistDetail(
    val artist: Artist,
    val popularTracks: List<Track> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val isFavorite: Boolean = false,
    val newRelease: Track? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val artworkUrl: String,
    val trackCount: Int = 0,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val id: String = "guest",
    val name: String = "Гость",
    val email: String = "Войдите для сохранения плейлистов",
    val avatarUrl: String = "",
    val isLoggedIn: Boolean = false
)

data class MonthlyStats(
    val monthName: String = "Ноябрь 2026",
    val topArtist: String = "Нет данных",
    val topArtistArtwork: String = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
    val topTrack: String = "Включайте треки",
    val topTrackArtwork: String = "",
    val topTrackPlays: Int = 0,
    val hoursListened: Double = 0.0,
    val topGenre: String = "Определяется",
    val genrePercent: Int = 0
)
