package com.cloudify.music.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cloudify.music.MainActivity
import com.cloudify.music.core.network.CloudifyApi
import com.cloudify.music.domain.model.Track
import com.cloudify.music.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class TrackDownloadManager(
    private val context: Context,
    private val musicRepository: MusicRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val notificationManager = NotificationManagerCompat.from(appContext)

    companion object {
        private const val DOWNLOAD_CHANNEL_ID = "cloudify_download_channel"
        private const val BATCH_NOTIFICATION_ID = 9999
    }

    init {
        createDownloadNotificationChannel()
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Загрузка музыки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогресс и статус скачивания музыки"
                setShowBadge(false)
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            notificationManager.notify(id, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun cancelSafely(id: Int) {
        try {
            notificationManager.cancel(id)
        } catch (_: Exception) {
        }
    }

    private fun getTrackNotificationId(trackId: String): Int {
        return (trackId.hashCode() and 0x7FFFFFFF) % 100000 + 2000
    }

    fun downloadTrack(track: Track) {
        // If track is already a local file
        if (track.isDownloaded ||
            (track.localFilePath != null && File(track.localFilePath).exists()) ||
            track.streamUrl.startsWith("file:", ignoreCase = true)
        ) {
            Toast.makeText(appContext, "Трек «${track.title}» уже сохранён в памяти", Toast.LENGTH_SHORT).show()
            return
        }

        val notifId = getTrackNotificationId(track.id)
        Toast.makeText(appContext, "Скачивание: ${track.title}...", Toast.LENGTH_SHORT).show()

        // Immediate starting notification in the notification shade
        val initialNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Скачивание: ${track.title}")
            .setContentText("Подготовка загрузки... (0%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(getPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        notifySafely(notifId, initialNotif)

        scope.launch {
            try {
                val musicDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: File(appContext.filesDir, "downloads").apply { mkdirs() }

                val safeFileName = "cloudify_${track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")}.mp3"
                val targetFile = File(musicDir, safeFileName)

                var downloadUrl = track.streamUrl
                if (!downloadUrl.contains("/api/stream") && !downloadUrl.contains("/api/download")) {
                    downloadUrl = CloudifyApi.getStreamUrl(track.streamUrl)
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("User-Agent", "CloudifyApp/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    showDownloadError(notifId, track.title, "Сбой сервера (${response.code})")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Ошибка загрузки сервера (${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val body = response.body
                if (body != null) {
                    val totalBytes = body.contentLength()
                    FileOutputStream(targetFile).use { output ->
                        body.byteStream().use { input ->
                            copyStreamWithProgress(
                                input = input,
                                output = output,
                                totalBytes = totalBytes
                            ) { bytesDownloaded, total, percent ->
                                val downloadedMb = bytesDownloaded.toDouble() / (1024 * 1024)
                                val text = if (total > 0) {
                                    val totalMb = total.toDouble() / (1024 * 1024)
                                    String.format(
                                        Locale.getDefault(),
                                        "%.1f МБ / %.1f МБ (%d%%)",
                                        downloadedMb,
                                        totalMb,
                                        percent
                                    )
                                } else {
                                    String.format(Locale.getDefault(), "%.1f МБ скачано", downloadedMb)
                                }

                                val progressNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
                                    .setContentTitle("Скачивание: ${track.title}")
                                    .setContentText(text)
                                    .setSmallIcon(android.R.drawable.stat_sys_download)
                                    .setContentIntent(getPendingIntent())
                                    .setPriority(NotificationCompat.PRIORITY_LOW)
                                    .setOnlyAlertOnce(true)
                                    .setOngoing(true)
                                    .setProgress(100, percent, total <= 0)
                                    .build()
                                notifySafely(notifId, progressNotif)
                            }
                        }
                    }

                    // Save record to Room
                    musicRepository.saveDownloadedTrack(track, targetFile.absolutePath)

                    // Success notification with final size
                    val finalMb = targetFile.length().toDouble() / (1024 * 1024)
                    val successNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
                        .setContentTitle("✓ Скачано: ${track.title}")
                        .setContentText("${track.artist} • ${String.format(Locale.getDefault(), "%.1f МБ", finalMb)}")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(getPendingIntent())
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .setProgress(0, 0, false)
                        .build()
                    notifySafely(notifId, successNotif)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "✓ Скачано: ${track.title} (доступно оффлайн)", Toast.LENGTH_LONG).show()
                    }

                    // Auto-dismiss completed notification after 6 seconds
                    delay(6000)
                    cancelSafely(notifId)
                }
            } catch (e: Exception) {
                showDownloadError(notifId, track.title, e.message ?: "Сбой соединения")
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Сбой скачивания: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadAllTracks(tracks: List<Track>) {
        val toDownload = tracks.filter {
            !it.isDownloaded &&
            (it.localFilePath == null || !File(it.localFilePath).exists()) &&
            !it.streamUrl.startsWith("file:", ignoreCase = true)
        }

        if (toDownload.isEmpty()) {
            Toast.makeText(appContext, "Все треки уже скачаны на устройство", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(appContext, "Начато скачивание ${toDownload.size} треков...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val musicDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(appContext.filesDir, "downloads").apply { mkdirs() }

            var downloadedCount = 0
            for ((index, track) in toDownload.withIndex()) {
                try {
                    val safeFileName = "cloudify_${track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")}.mp3"
                    val targetFile = File(musicDir, safeFileName)

                    var downloadUrl = track.streamUrl
                    if (!downloadUrl.contains("/api/stream") && !downloadUrl.contains("/api/download")) {
                        downloadUrl = CloudifyApi.getStreamUrl(track.streamUrl)
                    }

                    val request = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("User-Agent", "CloudifyApp/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val body = response.body!!
                        val totalBytes = body.contentLength()

                        FileOutputStream(targetFile).use { output ->
                            body.byteStream().use { input ->
                                copyStreamWithProgress(
                                    input = input,
                                    output = output,
                                    totalBytes = totalBytes
                                ) { bytesDownloaded, total, percent ->
                                    val downloadedMb = bytesDownloaded.toDouble() / (1024 * 1024)
                                    val text = if (total > 0) {
                                        val totalMb = total.toDouble() / (1024 * 1024)
                                        String.format(
                                            Locale.getDefault(),
                                            "%s • %.1f/%.1f МБ (%d%%)",
                                            track.title,
                                            downloadedMb,
                                            totalMb,
                                            percent
                                        )
                                    } else {
                                        String.format(
                                            Locale.getDefault(),
                                            "%s • %.1f МБ",
                                            track.title,
                                            downloadedMb
                                        )
                                    }

                                    val progressNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
                                        .setContentTitle("Скачивание (${index + 1}/${toDownload.size})")
                                        .setContentText(text)
                                        .setSmallIcon(android.R.drawable.stat_sys_download)
                                        .setContentIntent(getPendingIntent())
                                        .setPriority(NotificationCompat.PRIORITY_LOW)
                                        .setOnlyAlertOnce(true)
                                        .setOngoing(true)
                                        .setProgress(100, percent, total <= 0)
                                        .build()
                                    notifySafely(BATCH_NOTIFICATION_ID, progressNotif)
                                }
                            }
                        }

                        musicRepository.saveDownloadedTrack(track, targetFile.absolutePath)
                        downloadedCount++
                    }
                } catch (_: Exception) {
                    // Continue with other tracks in batch
                }
            }

            // Batch complete notification
            val batchCompleteNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
                .setContentTitle("✓ Скачивание завершено")
                .setContentText("Скачано $downloadedCount из ${toDownload.size} треков")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(getPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .build()
            notifySafely(BATCH_NOTIFICATION_ID, batchCompleteNotif)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "✓ Загрузка завершена! Скачано треков: $downloadedCount из ${toDownload.size}",
                    Toast.LENGTH_LONG
                ).show()
            }

            delay(6000)
            cancelSafely(BATCH_NOTIFICATION_ID)
        }
    }

    private fun showDownloadError(notifId: Int, title: String, errorMsg: String) {
        val errorNotif = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Ошибка скачивания: $title")
            .setContentText(errorMsg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(getPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .build()
        notifySafely(notifId, errorNotif)
    }

    private inline fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        crossinline onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit
    ) {
        val buffer = ByteArray(16384)
        var bytesDownloaded = 0L
        var lastUpdateTime = 0L
        var lastPercent = -1

        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            bytesDownloaded += read

            val now = System.currentTimeMillis()
            val percent = if (totalBytes > 0) {
                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }

            // Throttle notifications to at most 4 per second (every 250ms) or whenever percent changes
            if (now - lastUpdateTime >= 250 || (totalBytes > 0 && percent != lastPercent && percent % 2 == 0)) {
                lastUpdateTime = now
                lastPercent = percent
                onProgress(bytesDownloaded, totalBytes, percent)
            }
        }
        output.flush()
        onProgress(bytesDownloaded, totalBytes, if (totalBytes > 0) 100 else 0)
    }

    fun deleteDownloadedTrack(trackId: String) {
        scope.launch {
            musicRepository.removeDownloadedTrack(trackId)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "Трек удален из памяти", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
