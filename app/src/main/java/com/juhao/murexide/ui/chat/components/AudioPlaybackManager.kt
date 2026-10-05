package com.juhao.murexide.ui.chat.components

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioPlaybackState(
    val messageId: String? = null,
    val isPlaying: Boolean = false
)

object AudioPlaybackManager {

    private const val REFERER_KEY = "Referer"
    private const val REFERER_VALUE = "https://myapp.jwznb.com"

    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private fun buildHeaders(url: String): Map<String, String> {
        return if (
            url.contains("chat-img.jwznb.com") ||
            url.contains("jwznb.com") ||
            url.contains("myapp.jwznb.com")
        ) {
            mapOf(REFERER_KEY to REFERER_VALUE)
        } else {
            emptyMap()
        }
    }

    private fun getPlayer(context: Context, url: String): ExoPlayer {
        return player ?: ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(buildHeaders(url))
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            .build()
            .also { created ->
                created.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            _state.value = AudioPlaybackState()
                            currentUrl = null
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = AudioPlaybackState()
                        currentUrl = null
                    }
                })
                player = created
            }
    }

    fun toggle(context: Context, messageId: String, url: String) {
        val value = url.trim()
        if (value.isEmpty()) return

        val targetHeaders = buildHeaders(value)

        if (player != null) {
            val urlChanged = currentUrl != value
            if (urlChanged) {
                player?.release()
                player = null
            }
        }

        currentUrl = value

        val exoPlayer = getPlayer(context, value)

        if (_state.value.messageId == messageId && currentUrl == value) {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
            return
        }

        _state.value = AudioPlaybackState(messageId = messageId, isPlaying = true)
        exoPlayer.setMediaItem(MediaItem.fromUri(value))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun stop() {
        player?.stop()
        currentUrl = null
        _state.value = AudioPlaybackState()
    }

    fun release() {
        player?.release()
        player = null
        currentUrl = null
        _state.value = AudioPlaybackState()
    }
}