package com.cloudify.music.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cloudify.music.domain.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 180000L
)

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 180000L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val volume: Float = 1.0f,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSeconds: Int? = null,
    val isVideoMode: Boolean = false,
    val sourceName: String = "Любимое"
)

class MusicPlayerController private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // High compatibility HTTP DataSource for ExoPlayer with generous 60s timeout for first-time audio downloads
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(60000)
        .setReadTimeoutMs(60000)
        .setUserAgent("CloudifyApp/1.0")

    private val defaultDataSourceFactory by lazy {
        DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)
    }

    private val cacheDataSourceFactory by lazy {
        val cache = MediaCacheManager.getCache(applicationContext)
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val mediaSourceFactory by lazy {
        DefaultMediaSourceFactory(applicationContext)
            .setDataSourceFactory(cacheDataSourceFactory)
    }

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    var onTrackPlay: ((Track) -> Unit)? = null
    var onTrackListenedSeconds: ((Long) -> Unit)? = null

    private var currentTrackListenedMs = 0L
    private var hasRecordedPlayForCurrentTrack = false
    private var lastSecondTickTime = 0L

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var retryCount = 0
    private var consecutiveFailures = 0

    private val artworkCache = LruCache<String, ByteArray>(50)

    private fun fetchArtworkBytes(url: String, onDone: (ByteArray) -> Unit) {
        if (url.isBlank()) return
        val cached = artworkCache.get(url)
        if (cached != null) {
            onDone(cached)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(url)
                    .allowHardware(false)
                    .size(600, 600)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                        val bytes = stream.toByteArray()
                        artworkCache.put(url, bytes)
                        withContext(Dispatchers.Main) {
                            onDone(bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CloudifyPlayer", "Failed to load artwork bytes: ${e.message}")
            }
        }
    }

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(
                    isPlaying = isPlaying,
                    isBuffering = if (isPlaying) false else _state.value.isBuffering
                )
                if (isPlaying) {
                    consecutiveFailures = 0
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isBuffering = true)
                    }
                    Player.STATE_READY -> {
                        retryCount = 0
                        consecutiveFailures = 0
                        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else 180000L
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            isPlaying = exoPlayer.isPlaying,
                            durationMs = duration
                        )
                    }
                    Player.STATE_ENDED -> {
                        _state.value = _state.value.copy(isBuffering = false)
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("CloudifyPlayer", "Playback error: ${error.message}", error)
                _state.value = _state.value.copy(isBuffering = false, isPlaying = false)
                if (retryCount < 1) {
                    retryCount++
                    scope.launch {
                        delay(800L)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                } else {
                    retryCount = 0
                    consecutiveFailures++
                    // Only advance once if queue has multiple tracks; stop if continuous failure to prevent infinite crashing loop
                    if (consecutiveFailures < 2 && _state.value.queue.size > 1) {
                        next()
                    } else {
                        consecutiveFailures = 0
                        exoPlayer.stop()
                    }
                }
            }
        })
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track), sourceName: String? = null) {
        startPlaybackService()
        retryCount = 0
        currentTrackListenedMs = 0L
        hasRecordedPlayForCurrentTrack = false
        lastSecondTickTime = 0L
        val index = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _state.value = _state.value.copy(
            currentTrack = track,
            queue = newQueue,
            queueIndex = index,
            positionMs = 0L,
            durationMs = track.durationMs,
            isBuffering = true,
            sourceName = sourceName ?: _state.value.sourceName.ifBlank { "Любимое" }
        )

        _progress.value = PlaybackProgress(0L, track.durationMs)
        val cachedBytes = artworkCache.get(track.artworkUrl)
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(Uri.parse(track.artworkUrl))
            .apply {
                if (cachedBytes != null) {
                    setArtworkData(cachedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()

        val playbackUri = resolvePlaybackUri(track)
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(playbackUri)
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)

        // Asynchronously fetch artwork if not yet in cache, and update media item so lockscreen & control center display the cover
        fetchArtworkBytes(track.artworkUrl) { bytes ->
            if (_state.value.currentTrack?.id == track.id) {
                try {
                    val current = exoPlayer.currentMediaItem
                    if (current != null && current.mediaId == track.id) {
                        val updatedMeta = current.mediaMetadata.buildUpon()
                            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                        val updatedItem = current.buildUpon()
                            .setMediaMetadata(updatedMeta)
                            .build()
                        exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, updatedItem)
                    }
                } catch (e: Exception) {
                    Log.e("CloudifyPlayer", "Failed to update artwork on current media item", e)
                }
            }
        }

        // If queue has more than 1 track or repeat is active, add next media item to ExoPlayer
        // so that ExoPlayer's timeline natively has hasNextMediaItem == true.
        // This ensures the NEXT button is enabled on Android lock screen and notification shade widgets.
        if (newQueue.size > 1) {
            val nextTrack = newQueue[(index + 1) % newQueue.size]
            val nextCachedBytes = artworkCache.get(nextTrack.artworkUrl)
            val nextItem = MediaItem.Builder()
                .setMediaId("next_${nextTrack.id}")
                .setUri(playbackUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(nextTrack.title)
                        .setArtist(nextTrack.artist)
                        .setArtworkUri(Uri.parse(nextTrack.artworkUrl))
                        .apply {
                            if (nextCachedBytes != null) {
                                setArtworkData(nextCachedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                        }
                        .build()
                )
                .build()
            exoPlayer.addMediaItem(nextItem)

            // Pre-warm next track's artwork in memory cache
            fetchArtworkBytes(nextTrack.artworkUrl) {}
        }

        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun resolvePlaybackUri(track: Track): Uri {
        // 1. If track already explicitly points to local file
        if (!track.localFilePath.isNullOrBlank()) {
            val file = File(track.localFilePath)
            if (file.exists() && file.length() > 10000) {
                return Uri.fromFile(file)
            }
        }
        if (track.streamUrl.startsWith("file:", ignoreCase = true)) {
            return Uri.parse(track.streamUrl)
        }

        // 2. Check if track was saved to device storage by TrackDownloadManager
        try {
            val musicDir = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?: File(applicationContext.filesDir, "downloads")
            val safeFileName = "cloudify_${track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")}.mp3"
            val localCandidate = File(musicDir, safeFileName)
            if (localCandidate.exists() && localCandidate.length() > 10000) {
                return Uri.fromFile(localCandidate)
            }
        } catch (_: Exception) {}

        // 3. Fallback to network stream URL
        return Uri.parse(track.streamUrl)
    }

    private fun preCacheNext(nextIndex: Int, queue: List<Track>) {
        if (nextIndex in queue.indices) {
            val nextTrack = queue[nextIndex]
            if (nextTrack.streamUrl.startsWith("http://") || nextTrack.streamUrl.startsWith("https://")) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val url = URL(nextTrack.streamUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "HEAD"
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.responseCode
                        conn.disconnect()
                    } catch (e: Exception) {
                        // Pre-cache is optional, ignore errors
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (_state.value.currentTrack != null) {
                exoPlayer.play()
            } else if (_state.value.queue.isNotEmpty()) {
                playTrack(_state.value.queue[0], _state.value.queue)
            }
        }
    }

    fun next() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        if (q.size == 1) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
            return
        }
        val nextIdx = (_state.value.queueIndex + 1) % q.size
        playTrack(q[nextIdx], q, _state.value.sourceName)
    }

    fun previous() {
        val q = _state.value.queue
        if (q.isEmpty()) return
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
            return
        }
        if (q.size == 1) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
            return
        }
        var prevIdx = _state.value.queueIndex - 1
        if (prevIdx < 0) {
            prevIdx = q.size - 1
        }
        playTrack(q[prevIdx], q, _state.value.sourceName)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _progress.value = _progress.value.copy(positionMs = positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        _state.value = _state.value.copy(isShuffle = newShuffle)
    }

    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = nextMode)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    fun toggleVideoMode() {
        _state.value = _state.value.copy(isVideoMode = !_state.value.isVideoMode)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _state.value = _state.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemainingSeconds = minutes * 60
        )
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _state.value = _state.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            exoPlayer.pause()
            _state.value = _state.value.copy(sleepTimerMinutes = null, sleepTimerRemainingSeconds = null)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _state.value = _state.value.copy(sleepTimerMinutes = null, sleepTimerRemainingSeconds = null)
    }

    private fun handleTrackEnded() {
        if (!hasRecordedPlayForCurrentTrack) {
            hasRecordedPlayForCurrentTrack = true
            _state.value.currentTrack?.let { track ->
                onTrackPlay?.invoke(track)
            }
        }
        when (_state.value.repeatMode) {
            RepeatMode.ONE -> {
                currentTrackListenedMs = 0L
                hasRecordedPlayForCurrentTrack = false
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                val q = _state.value.queue
                if (_state.value.queueIndex + 1 < q.size) {
                    next()
                } else {
                    exoPlayer.seekTo(0)
                    exoPlayer.pause()
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val now = System.currentTimeMillis()
                    if (lastSecondTickTime == 0L) {
                        lastSecondTickTime = now
                    } else {
                        val elapsed = now - lastSecondTickTime
                        if (elapsed >= 1000L) {
                            val secondsPassed = elapsed / 1000L
                            lastSecondTickTime = now
                            onTrackListenedSeconds?.invoke(secondsPassed)
                        }
                    }

                    currentTrackListenedMs += 250L

                    // Real listening requirement: must listen for at least 20 seconds to count as played!
                    if (currentTrackListenedMs >= 20_000L && !hasRecordedPlayForCurrentTrack) {
                        hasRecordedPlayForCurrentTrack = true
                        _state.value.currentTrack?.let { track ->
                            onTrackPlay?.invoke(track)
                        }
                    }
                } else {
                    lastSecondTickTime = 0L
                }

                val current = exoPlayer.currentPosition
                val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _progress.value.durationMs
                _progress.value = PlaybackProgress(positionMs = current, durationMs = duration)
                delay(250L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(applicationContext, MusicPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.w("CloudifyPlayer", "Could not start playback service", e)
        }
    }

    companion object {
        @Volatile
        private var instance: MusicPlayerController? = null

        fun getInstance(context: Context): MusicPlayerController {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerController(context).also { instance = it }
            }
        }
    }
}

object MediaCacheManager {
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "cloudify_audio_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024L * 1024L) // 500 MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
            }
        }
    }
}
