package com.cloudify.music.data.repository

import com.cloudify.music.core.database.*
import com.cloudify.music.core.network.CloudifyApi
import com.cloudify.music.domain.model.*
import com.cloudify.music.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.cloudify.music.core.util.CloudSyncManager
import android.os.Environment
import java.io.File
import java.util.Calendar

class MusicRepositoryImpl(
    private val api: CloudifyApi,
    private val dao: MusicDao,
    private val context: android.content.Context? = null
) : MusicRepository {

    private val prefs by lazy {
        context?.getSharedPreferences("cloudify_stats", android.content.Context.MODE_PRIVATE)
    }

    override suspend fun addListenedSeconds(seconds: Long) {
        if (seconds <= 0) return
        val current = getTotalListenedSeconds()
        prefs?.edit()?.putLong("total_listened_seconds", current + seconds)?.apply()
    }

    override suspend fun getTotalListenedSeconds(): Long {
        return prefs?.getLong("total_listened_seconds", 0L) ?: 0L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun triggerAutoCloudSync() {
        val ctx = context ?: return
        scope.launch {
            try {
                val userRepo = UserRepositoryImpl(ctx)
                val user = userRepo.currentUserFlow.first()
                if (user.isLoggedIn && (user.id.isNotBlank() || user.email.isNotBlank())) {
                    CloudSyncManager(ctx).syncToServer(user)
                }
            } catch (_: Exception) {}
        }
    }

    // Instant in-memory state of favorite track IDs
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    override val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    // Instant in-memory state of downloaded track IDs
    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    // Instant in-memory state of favorite artist names
    private val _favoriteArtistNames = MutableStateFlow<Set<String>>(emptySet())
    override val favoriteArtistNames: StateFlow<Set<String>> = _favoriteArtistNames.asStateFlow()

    init {
        scope.launch {
            dao.getAllFavorites().collect { favorites ->
                _favoriteIds.value = favorites.map { it.id }.toSet()
            }
        }
        scope.launch {
            dao.getAllDownloadedTracks().collect { downloads ->
                _downloadedIds.value = downloads.map { it.id }.toSet()
            }
        }
        scope.launch {
            dao.getAllFavoriteArtists().collect { artists ->
                _favoriteArtistNames.value = artists.map { it.name }.toSet()
            }
        }
    }

    override suspend fun toggleFavorite(track: Track) {
        val current = _favoriteIds.value
        val isCurrentlyFav = current.contains(track.id)

        // Instant synchronous update (0ms UI latency)
        if (isCurrentlyFav) {
            _favoriteIds.value = current - track.id
        } else {
            _favoriteIds.value = current + track.id
        }

        scope.launch {
            if (isCurrentlyFav) {
                dao.deleteFavorite(track.id)
            } else {
                dao.insertFavorite(
                    FavoriteTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        streamUrl = track.streamUrl,
                        durationMs = track.durationMs,
                        genre = track.genre,
                        lyrics = track.lyrics
                    )
                )
            }
            triggerAutoCloudSync()
        }
    }

    override suspend fun addFavorite(track: Track, customAddedAt: Long?) {
        val current = _favoriteIds.value
        _favoriteIds.value = current + track.id
        dao.insertFavorite(
            FavoriteTrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs,
                genre = track.genre,
                lyrics = track.lyrics,
                addedAt = customAddedAt ?: System.currentTimeMillis()
            )
        )
        triggerAutoCloudSync()
    }

    override suspend fun toggleFavoriteArtist(artist: Artist) {
        val current = _favoriteArtistNames.value
        val isFav = current.contains(artist.name)

        if (isFav) {
            _favoriteArtistNames.value = current - artist.name
        } else {
            _favoriteArtistNames.value = current + artist.name
        }

        scope.launch {
            if (isFav) {
                dao.deleteFavoriteArtist(artist.name)
            } else {
                dao.insertFavoriteArtist(
                    FavoriteArtistEntity(
                        name = artist.name,
                        artworkUrl = artist.artworkUrl,
                        genre = artist.genre,
                        monthlyListeners = artist.monthlyListeners
                    )
                )
            }
            triggerAutoCloudSync()
        }
    }

    override fun getFavoriteArtists(): Flow<List<Artist>> {
        return dao.getAllFavoriteArtists().map { entities ->
            entities.map { entity ->
                Artist(
                    id = entity.name.hashCode().toString(),
                    name = entity.name,
                    artworkUrl = entity.artworkUrl,
                    genre = entity.genre,
                    monthlyListeners = entity.monthlyListeners,
                    isFavorite = true
                )
            }
        }
    }

    override fun getDownloadedTracks(): Flow<List<Track>> {
        return dao.getAllDownloadedTracks().map { entities ->
            val musicDir = context?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: context?.filesDir?.resolve("downloads")
            entities.map { entity ->
                val localFile = File(entity.localFilePath)
                val playUrl = if (localFile.exists()) localFile.toURI().toString() else entity.localFilePath
                val safeId = entity.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val diskArtFile = if (musicDir != null) File(musicDir, "cloudify_art_${safeId}.jpg") else null
                val resolvedArtPath = when {
                    !entity.localArtworkPath.isNullOrBlank() && File(entity.localArtworkPath).exists() -> entity.localArtworkPath
                    diskArtFile != null && diskArtFile.exists() && diskArtFile.length() > 200 -> diskArtFile.absolutePath
                    else -> null
                }
                Track(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    artworkUrl = entity.artworkUrl,
                    streamUrl = playUrl,
                    durationMs = entity.durationMs,
                    isDownloaded = true,
                    localFilePath = entity.localFilePath,
                    localArtworkPath = resolvedArtPath,
                    genre = entity.genre
                )
            }
        }
    }

    override suspend fun saveDownloadedTrack(track: Track, localFilePath: String, localArtworkPath: String?) {
        _downloadedIds.value = _downloadedIds.value + track.id
        val musicDir = context?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context?.filesDir?.resolve("downloads")
        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val diskArtFile = if (musicDir != null) File(musicDir, "cloudify_art_${safeId}.jpg") else null
        val resolvedArt = when {
            !localArtworkPath.isNullOrBlank() && File(localArtworkPath).exists() -> localArtworkPath
            !track.localArtworkPath.isNullOrBlank() && File(track.localArtworkPath).exists() -> track.localArtworkPath
            diskArtFile != null && diskArtFile.exists() && diskArtFile.length() > 200 -> diskArtFile.absolutePath
            else -> null
        }
        dao.insertDownloadedTrack(
            DownloadedTrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                localFilePath = localFilePath,
                localArtworkPath = resolvedArt,
                durationMs = track.durationMs,
                genre = track.genre
            )
        )
    }

    override suspend fun removeDownloadedTrack(trackId: String) {
        _downloadedIds.value = _downloadedIds.value - trackId
        dao.deleteDownloadedTrack(trackId)
        val musicDir = context?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context?.filesDir?.resolve("downloads")
        if (musicDir != null) {
            val safeId = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val audioFile = File(musicDir, "cloudify_${safeId}.mp3")
            val artFile = File(musicDir, "cloudify_art_${safeId}.jpg")
            if (audioFile.exists()) audioFile.delete()
            if (artFile.exists()) artFile.delete()
        }
    }

    override suspend fun search(query: String): List<Track> {
        return try {
            val dtos = api.searchTracks(query)
            dtos.map { dto ->
                Track(
                    id = dto.id,
                    title = dto.title,
                    artist = dto.uploader,
                    artworkUrl = CloudifyApi.getCoverUrl(dto.thumbnail),
                    streamUrl = CloudifyApi.getStreamUrl(dto.url),
                    genre = detectGenre(dto.title)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getWaveTracks(): List<Track> {
        return try {
            val dtos = api.getWaveTracks()
            if (dtos.isNotEmpty()) {
                dtos.map { dto ->
                    Track(
                        id = dto.id,
                        title = dto.title,
                        artist = dto.uploader,
                        artworkUrl = CloudifyApi.getCoverUrl(dto.thumbnail),
                        streamUrl = CloudifyApi.getStreamUrl(dto.url),
                        genre = "SoundCloud Hits"
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTrendingTracks(): List<Track> {
        return search("GONE.Fludd Miyagi phonk hits")
    }

    override suspend fun getPopularForYou(): List<Track> {
        val history = dao.getAllHistoryList()
        val query = if (history.isNotEmpty()) {
            history.take(3).joinToString(" ") { it.artist }
        } else {
            "GONE.Fludd Miyagi Andy Panda"
        }
        return search(query).distinctBy { it.id }
    }

    override fun getPopularArtists(): List<Artist> {
        return listOf(
            Artist("a_dabbackwood", "dabbackwood", CloudifyApi.getCoverUrl("https://i1.sndcdn.com/avatars-zmVG9KSLGIaTyCxA-schJmA-t500x500.jpg"), "Hip-Hop / Rap", "215 тыс. слушателей"),
            Artist("a_gone", "GONE.Fludd", CloudifyApi.getCoverUrl("https://i1.sndcdn.com/avatars-qcDcKZDyCbj8ykiQ-FEOkHA-t500x500.jpg"), "Hip-Hop / Rap", "839.6 тыс. слушателей"),
            Artist("a_miyagi", "Miyagi & Эндшпиль", CloudifyApi.getCoverUrl("https://i1.sndcdn.com/avatars-zK5Rj5tpjLZYizRW-a6bEXA-t500x500.jpg"), "Hip-Hop", "3.8M слушателей"),
            Artist("a_kordhell", "KORDHELL", CloudifyApi.getCoverUrl("https://i1.sndcdn.com/avatars-IwkAewk5iACtjj7t-mjkT9g-t500x500.jpg"), "Phonk", "4.2M слушателей"),
            Artist("a_dvrst", "DVRST", CloudifyApi.getCoverUrl("https://i1.sndcdn.com/avatars-91G3KMdmclZj1XN2-blvgeQ-t500x500.jpg"), "Drift Phonk", "2.9M слушателей")
        )
    }

    override suspend fun getArtistDetail(artistName: String): ArtistDetail {
        val tracks = search(artistName)
        val albumsSearch = search("$artistName album")
        val isFav = _favoriteArtistNames.value.contains(artistName)

        val artistObj = getPopularArtists().find { it.name.equals(artistName, ignoreCase = true) }
            ?: Artist(
                id = artistName.hashCode().toString(),
                name = artistName,
                artworkUrl = tracks.firstOrNull()?.artworkUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400",
                genre = "Исполнитель",
                monthlyListeners = "839.6 тыс. слушателей",
                isFavorite = isFav
            )

        val albums = if (albumsSearch.isNotEmpty()) {
            albumsSearch.take(8)
                .distinctBy { it.title.trim().lowercase() }
                .mapIndexed { i, it ->
                    val cleanTitle = it.title
                        .replace(Regex("(?i)^${Regex.escape(artistName)}\\s*[-–—:]\\s*"), "")
                        .replace(Regex("(?i)\\s*\\(?(full\\s*)?(album|альбом|ep|lp|ost|soundtrack)\\)?"), "")
                        .trim(' ', '-', '—', '–')
                        .ifBlank { it.title }
                    Playlist(
                        id = "album_${it.id}_$i",
                        name = cleanTitle,
                        artworkUrl = it.artworkUrl.ifBlank { artistObj.artworkUrl },
                        trackCount = (6..14).random(),
                        isCustom = false,
                        createdAt = System.currentTimeMillis() - (i * 86400000L * 90L)
                    )
                }
        } else {
            tracks.chunked(3).take(4).mapIndexed { i, chunk ->
                val rep = chunk.first()
                Playlist(
                    id = "album_${rep.id}_$i",
                    name = rep.title,
                    artworkUrl = rep.artworkUrl.ifBlank { artistObj.artworkUrl },
                    trackCount = (4..10).random(),
                    isCustom = false,
                    createdAt = System.currentTimeMillis() - (i * 86400000L * 120L)
                )
            }
        }

        return ArtistDetail(
            artist = artistObj.copy(isFavorite = isFav),
            popularTracks = tracks.take(5),
            allTracks = tracks,
            albums = albums,
            isFavorite = isFav,
            newRelease = tracks.firstOrNull()
        )
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return dao.getAllFavorites().map { entities ->
            val musicDir = context?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: context?.filesDir?.resolve("downloads")
            entities.map { entity ->
                val safeId = entity.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val isDown = _downloadedIds.value.contains(entity.id)
                val diskAudioFile = if (musicDir != null) File(musicDir, "cloudify_${safeId}.mp3") else null
                val hasLocalAudio = diskAudioFile != null && diskAudioFile.exists() && diskAudioFile.length() > 10000
                val diskArtFile = if (musicDir != null) File(musicDir, "cloudify_art_${safeId}.jpg") else null
                val resolvedArt = when {
                    !entity.localArtworkPath.isNullOrBlank() && File(entity.localArtworkPath).exists() -> entity.localArtworkPath
                    diskArtFile != null && diskArtFile.exists() && diskArtFile.length() > 200 -> diskArtFile.absolutePath
                    else -> null
                }
                Track(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    artworkUrl = entity.artworkUrl,
                    streamUrl = if (hasLocalAudio) diskAudioFile!!.toURI().toString() else entity.streamUrl,
                    durationMs = entity.durationMs,
                    isFavorite = true,
                    isDownloaded = isDown || hasLocalAudio,
                    localFilePath = if (hasLocalAudio) diskAudioFile!!.absolutePath else null,
                    localArtworkPath = resolvedArt,
                    genre = entity.genre,
                    lyrics = entity.lyrics
                )
            }
        }
    }

    override fun getPlaylists(): Flow<List<Playlist>> {
        return dao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    artworkUrl = entity.artworkUrl,
                    trackCount = entity.trackCount,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override suspend fun createPlaylist(name: String, artworkUrl: String) {
        val id = "pl_${System.currentTimeMillis()}"
        val art = artworkUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300" }
        dao.insertPlaylist(PlaylistEntity(id = id, name = name, artworkUrl = art, trackCount = 0))
        triggerAutoCloudSync()
    }

    override suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
        triggerAutoCloudSync()
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track) {
        dao.insertPlaylistTrack(PlaylistTrackCrossRef(playlistId, track.id))
        triggerAutoCloudSync()
    }

    override suspend fun getPlaylistTrackIds(playlistId: String): List<String> {
        return dao.getPlaylistTrackIds(playlistId)
    }

    override fun getHistory(): Flow<List<Track>> {
        return dao.getRecentHistory().map { entities ->
            entities.distinctBy { it.trackId }.map { entity ->
                Track(
                    id = entity.trackId,
                    title = entity.title,
                    artist = entity.artist,
                    artworkUrl = entity.artworkUrl,
                    streamUrl = entity.streamUrl,
                    genre = entity.genre
                )
            }
        }
    }

    override suspend fun recordPlayed(track: Track) {
        dao.insertHistory(
            HistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                genre = track.genre
            )
        )
    }

    override fun getRecentSearches(): Flow<List<String>> {
        return dao.getRecentSearches()
    }

    override suspend fun saveSearch(query: String) {
        if (query.isNotBlank()) {
            dao.insertSearchQuery(SearchQueryEntity(query.trim()))
        }
    }

    override suspend fun deleteSearch(query: String) {
        dao.deleteSearchQuery(query)
    }

    override suspend fun getMonthlyStats(): MonthlyStats {
        val history = dao.getAllHistoryList()
        val calendar = Calendar.getInstance()
        val monthNames = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        val currentMonthName = "${monthNames[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"

        // Calculate REAL listening hours based on actual seconds listened
        val totalRealSeconds = getTotalListenedSeconds()
        val hoursListened = if (totalRealSeconds > 0) {
            Math.round((totalRealSeconds / 3600.0) * 10.0) / 10.0
        } else if (history.isNotEmpty()) {
            Math.round((history.size * 2.0 / 60.0) * 10.0) / 10.0
        } else {
            0.0
        }

        if (history.isEmpty()) {
            return MonthlyStats(
                monthName = currentMonthName,
                topArtist = "Пока нет данных",
                topArtistArtwork = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                topTrack = "Включайте треки",
                topTrackArtwork = "",
                topTrackPlays = 0,
                hoursListened = hoursListened,
                topGenre = "Определяется",
                genrePercent = 0
            )
        }

        val totalPlays = history.size

        val artistCounts = history.groupingBy { it.artist }.eachCount()
        val topArtistEntry = artistCounts.maxByOrNull { it.value }
        val topArtist = topArtistEntry?.key ?: "Разные исполнители"

        // Discover true avatar for top artist
        var topArtistAvatar = ""
        if (topArtist != "Разные исполнители" && topArtist != "Пока нет данных") {
            try {
                val fav = dao.getFavoriteArtist(topArtist)
                if (fav != null && fav.artworkUrl.isNotBlank()) {
                    topArtistAvatar = fav.artworkUrl
                }
            } catch (_: Exception) {}

            if (topArtistAvatar.isBlank()) {
                try {
                    val trackMatch = dao.getTrackByArtist(topArtist)
                    if (trackMatch != null && trackMatch.artworkUrl.isNotBlank()) {
                        topArtistAvatar = trackMatch.artworkUrl
                    }
                } catch (_: Exception) {}
            }

            if (topArtistAvatar.isBlank()) {
                val match = history.firstOrNull { it.artist.equals(topArtist, ignoreCase = true) }
                if (match != null && match.artworkUrl.isNotBlank()) {
                    topArtistAvatar = match.artworkUrl
                }
            }

            if (topArtistAvatar.isBlank()) {
                val popularMatch = getPopularArtists().find { it.name.equals(topArtist, ignoreCase = true) }
                if (popularMatch != null && popularMatch.artworkUrl.isNotBlank()) {
                    topArtistAvatar = popularMatch.artworkUrl
                }
            }

            // Fallback: search SoundCloud for artist tracks and pick cover
            if (topArtistAvatar.isBlank()) {
                try {
                    val artistTracks = search(topArtist)
                    if (artistTracks.isNotEmpty() && artistTracks[0].artworkUrl.isNotBlank()) {
                        topArtistAvatar = artistTracks[0].artworkUrl
                    }
                } catch (_: Exception) {}
            }
        }
        if (topArtistAvatar.isBlank()) {
            topArtistAvatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300"
        }

        val trackCounts = history.groupingBy { it.trackId }.eachCount()
        val topTrackEntry = trackCounts.maxByOrNull { it.value }
        val topTrackHistory = history.firstOrNull { it.trackId == topTrackEntry?.key }
        val topTrackTitle = topTrackHistory?.title ?: "Любимый трек"
        val topTrackArtwork = topTrackHistory?.artworkUrl ?: ""
        val topTrackPlays = topTrackEntry?.value ?: 1

        val genreCounts = history.groupingBy { it.genre.ifBlank { "Hip-Hop / Phonk" } }.eachCount()
        val topGenreEntry = genreCounts.maxByOrNull { it.value }
        val topGenre = topGenreEntry?.key ?: "Hip-Hop / Phonk"
        val genrePercent = if (totalPlays > 0) ((topGenreEntry?.value ?: 0) * 100 / totalPlays) else 100

        return MonthlyStats(
            monthName = currentMonthName,
            topArtist = topArtist,
            topArtistArtwork = topArtistAvatar,
            topTrack = topTrackTitle,
            topTrackArtwork = topTrackArtwork,
            topTrackPlays = topTrackPlays,
            hoursListened = hoursListened,
            topGenre = topGenre,
            genrePercent = genrePercent
        )
    }

    private fun detectGenre(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("phonk") -> "Phonk"
            lower.contains("remix") -> "Remix"
            lower.contains("lofi") || lower.contains("lo-fi") -> "Lofi"
            lower.contains("rap") || lower.contains("drill") || lower.contains("fludd") || lower.contains("dabback") -> "Hip-Hop"
            lower.contains("rock") || lower.contains("punk") -> "Rock"
            lower.contains("pop") -> "Pop"
            else -> "SoundCloud"
        }
    }
}
