package com.cloudify.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.cloudify.music.CloudifyApp
import com.cloudify.music.R

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as? CloudifyApp
        val isYellow = app?.userRepository?.getCurrentThemeColor()?.equals("yellow", ignoreCase = true) == true
        try {
            val notifIconRes = if (isYellow) R.drawable.ic_launcher_foreground_yellow else R.drawable.ic_cloudify_notification
            val provider = DefaultMediaNotificationProvider(this)
            provider.setSmallIcon(notifIconRes)
            setMediaNotificationProvider(provider)
        } catch (_: Exception) {}

        startImmediateForeground()

        val controller = MusicPlayerController.getInstance(this)

        val forwardingPlayer = object : ForwardingPlayer(controller.exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNext(): Boolean = true
            override fun hasNextMediaItem(): Boolean = true
            override fun hasNextWindow(): Boolean = true

            override fun hasPrevious(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
            override fun hasPreviousWindow(): Boolean = true

            override fun seekToNext() {
                controller.next()
            }

            override fun seekToNextMediaItem() {
                controller.next()
            }

            override fun seekToPrevious() {
                controller.previous()
            }

            override fun seekToPreviousMediaItem() {
                controller.previous()
            }
        }

        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = sessionActivityIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controllerInfo)
                val availableSessionCommands = connectionResult.availableSessionCommands
                val availablePlayerCommands = connectionResult.availablePlayerCommands
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.accept(
                    availableSessionCommands,
                    availablePlayerCommands
                )
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                when (playerCommand) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                        controller.next()
                        return SessionResult.RESULT_SUCCESS
                    }
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        controller.previous()
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
            }
        }

        val builder = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(callback)

        if (pendingIntent != null) {
            builder.setSessionActivity(pendingIntent)
        }

        mediaSession = builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение Cloudify",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Управление воспроизведением музыки"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startImmediateForeground() {
        try {
            val app = application as? CloudifyApp
            val isYellow = app?.userRepository?.getCurrentThemeColor()?.equals("yellow", ignoreCase = true) == true
            val smallIconRes = if (isYellow) R.drawable.ic_launcher_foreground_yellow else R.drawable.ic_cloudify_notification

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cloudify")
                .setContentText("Запуск воспроизведения...")
                .setSmallIcon(smallIconRes)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Foreground start fail safety
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "cloudify_music_playback"
        const val NOTIFICATION_ID = 1001
    }
}
