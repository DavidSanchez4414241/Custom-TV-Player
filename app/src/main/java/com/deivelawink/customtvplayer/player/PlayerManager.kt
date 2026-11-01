package com.deivelawink.customtvplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.deivelawink.customtvplayer.streamlink.ResolvedStream

@OptIn(UnstableApi::class)
object PlayerManager {
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        return player ?: ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory()))
            .build()
            .also {
                it.repeatMode = Player.REPEAT_MODE_OFF
                player = it
            }
    }

    fun prepare(context: Context, stream: ResolvedStream, view: PlayerView) {
        val exoPlayer = get(context)
        view.player = exoPlayer
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val playbackUrl = stream.playbackUrl
            .removePrefix("hls://")
            .removePrefix("HLS://")
        val mediaItem = MediaItem.fromUri(playbackUrl)
        val source = if (stream.isHls || playbackUrl.substringBefore('?').endsWith(".m3u8", true)) {
            HlsMediaSource.Factory(httpFactory(stream.headers)).createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(httpFactory(stream.headers)).createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun togglePause() {
        player?.let { it.playWhenReady = !it.playWhenReady }
    }

    fun stop() {
        player?.stop()
        player?.clearVideoSurface()
    }

    fun release() {
        player?.release()
        player = null
    }

    private fun httpFactory(headers: Map<String, String> = emptyMap()): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                ?: "CustomTVPlayer/2.0")
            .setDefaultRequestProperties(headers)
}
