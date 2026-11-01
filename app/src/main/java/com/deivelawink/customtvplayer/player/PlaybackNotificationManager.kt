package com.deivelawink.customtvplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import androidx.core.app.NotificationCompat
import com.deivelawink.customtvplayer.MainActivity
import com.deivelawink.customtvplayer.R

object PlaybackNotificationManager {
    private const val CHANNEL_ID = "playback"
    private const val NOTIFICATION_ID = 2001
    private var session: MediaSession? = null

    fun show(context: Context, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Reproducción",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val mediaSession = session ?: MediaSession(appContext, "CustomTVPlayer").also {
            it.setCallback(object : MediaSession.Callback() {
                override fun onPause() = PlayerManager.togglePause()
                override fun onPlay() = PlayerManager.togglePause()
                override fun onStop() = stop(appContext)
            })
            it.isActive = true
            session = it
        }
        val openIntent = PendingIntent.getActivity(
            appContext,
            1,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = actionIntent(appContext, ACTION_PAUSE)
        val stopIntent = actionIntent(appContext, ACTION_STOP)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Reproduciendo en CustomTVPlayer")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(appContext, android.R.drawable.ic_media_pause),
                        "Pausa",
                        pauseIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(appContext, android.R.drawable.ic_menu_close_clear_cancel),
                        "Detener",
                        stopIntent
                    ).build()
                )
                .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1))
                .build()
        } else {
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Reproduciendo en CustomTVPlayer")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun handleAction(context: Context, action: String) {
        when (action) {
            ACTION_PAUSE -> PlayerManager.togglePause()
            ACTION_STOP -> stop(context)
        }
    }

    fun stop(context: Context) {
        PlayerManager.stop()
        context.applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
        session?.isActive = false
        session?.release()
        session = null
    }

    private fun actionIntent(context: Context, action: String) =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, PlaybackNotificationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val ACTION_PAUSE = "com.deivelawink.customtvplayer.PAUSE"
    private const val ACTION_STOP = "com.deivelawink.customtvplayer.STOP"
}
