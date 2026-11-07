package com.cloudify.music.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val genre: String,
    val lyrics: String,
    val localArtworkPath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val localFilePath: String,
    val localArtworkPath: String? = null,
    val durationMs: Long = 180000L,
    val genre: String = "Music",
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_artists")
data class FavoriteArtistEntity(
    @PrimaryKey val name: String,
    val artworkUrl: String,
    val genre: String = "Исполнитель",
    val monthlyListeners: String = "850 тыс. слушателей",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val genre: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
