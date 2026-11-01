package com.deivelawink.customtvplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlaybackNotificationManager.handleAction(context, intent.action.orEmpty())
    }
}
