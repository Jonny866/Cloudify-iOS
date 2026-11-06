package com.cloudify.music.core.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class TrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("uploader") val uploader: String,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("url") val url: String
)

data class UserSyncDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("avatarUrl") val avatarUrl: String = ""
)

data class FavoriteSyncDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("artworkUrl") val artworkUrl: String = "",
    @SerializedName("streamUrl") val streamUrl: String = "",
    @SerializedName("durationMs") val durationMs: Long = 180000L,
    @SerializedName("genre") val genre: String = "SoundCloud",
    @SerializedName("lyrics") val lyrics: String = "",
    @SerializedName("addedAt") val addedAt: Long = 0L
)

data class PlaylistSyncDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("artworkUrl") val artworkUrl: String = "",
    @SerializedName("createdAt") val createdAt: Long = 0L,
    @SerializedName("trackIds") val trackIds: List<String> = emptyList()
)

data class ArtistSyncDto(
    @SerializedName("name") val name: String,
    @SerializedName("artworkUrl") val artworkUrl: String = "",
    @SerializedName("genre") val genre: String = "Исполнитель",
    @SerializedName("monthlyListeners") val monthlyListeners: String = "",
    @SerializedName("addedAt") val addedAt: Long = 0L
)

data class SyncPayloadDto(
    @SerializedName("user") val user: UserSyncDto,
    @SerializedName("favorites") val favorites: List<FavoriteSyncDto> = emptyList(),
    @SerializedName("playlists") val playlists: List<PlaylistSyncDto> = emptyList(),
    @SerializedName("favoriteArtists") val favoriteArtists: List<ArtistSyncDto> = emptyList()
)

data class SyncResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("user") val user: UserSyncDto? = null,
    @SerializedName("favorites") val favorites: List<FavoriteSyncDto> = emptyList(),
    @SerializedName("playlists") val playlists: List<PlaylistSyncDto> = emptyList(),
    @SerializedName("favoriteArtists") val favoriteArtists: List<ArtistSyncDto> = emptyList(),
    @SerializedName("savedFavorites") val savedFavorites: Int = 0,
    @SerializedName("savedPlaylists") val savedPlaylists: Int = 0
)

interface CloudifyApi {

    @GET("/api/search")
    suspend fun searchTracks(@Query("q") query: String): List<TrackDto>

    @GET("/api/wave")
    suspend fun getWaveTracks(): List<TrackDto>

    @POST("/api/user/sync")
    suspend fun syncUserData(@Body payload: SyncPayloadDto): SyncResponseDto

    @GET("/api/user/sync")
    suspend fun getUserData(
        @Query("user_id") userId: String? = null,
        @Query("email") email: String? = null
    ): SyncResponseDto

    companion object {
        const val BASE_URL = "http://de-bots6.h1cloud.net:25066"

        fun getStreamUrl(webpageUrl: String, quality: String = "high"): String {
            val qParam = when {
                quality.contains("320") || quality.lowercase() == "high" -> "high"
                quality.contains("64") || quality.lowercase() == "low" -> "low"
                else -> "standard"
            }
            val encoded = URLEncoder.encode(webpageUrl, "UTF-8")
            return "$BASE_URL/api/stream?url=$encoded&quality=$qParam"
        }

        fun getCoverUrl(originalThumbnailUrl: String?): String {
            if (originalThumbnailUrl.isNullOrEmpty()) {
                return "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
            }
            val encoded = URLEncoder.encode(originalThumbnailUrl, "UTF-8")
            return "$BASE_URL/api/cover?url=$encoded"
        }

        fun create(): CloudifyApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CloudifyApi::class.java)
        }
    }
}
