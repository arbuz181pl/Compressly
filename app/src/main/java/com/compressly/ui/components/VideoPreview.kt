package com.compressly.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.compressly.domain.TrimRange
import com.compressly.ui.theme.AppShapes
import kotlinx.coroutines.delay

private const val POSITION_POLL_MS = 50L

/**
 * Video preview that plays only the trimmed range.
 *
 * Playback is clamped to `[startMs, endMs]`: pressing play seeks to the in-point, and reaching
 * the out-point pauses instead of running on into the discarded footage.
 */
// PlayerView and AspectRatioFrameLayout are still @UnstableApi in Media3 1.11.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPreview(
    uri: android.net.Uri,
    trim: TrimRange,
    isPlaying: Boolean,
    seekToMs: Long,
    onPositionChange: (Long) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    val liveTrim by rememberUpdatedState(trim)
    val liveSeekTo by rememberUpdatedState(seekToMs)
    val liveOnPosition by rememberUpdatedState(onPositionChange)
    val liveOnPlaying by rememberUpdatedState(onPlayingChange)
    val liveOnFailure by rememberUpdatedState(onFailure)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                liveOnFailure()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    liveOnPlaying(false)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Play/pause driven by the button.
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Always seek to the requested position when starting. The ViewModel hands back
            // the in-point whenever the playhead sits at or past the out-point, so pressing
            // play after the preview finished always restarts from the beginning of the trim.
            player.seekTo(liveSeekTo.coerceIn(liveTrim.startMs, liveTrim.endMs))
            player.play()
        } else {
            player.pause()
        }
    }

    // Scrubbing from the timeline seeks the player, unless it is already playing there.
    LaunchedEffect(seekToMs) {
        if (!isPlaying && player.currentPosition != seekToMs) {
            player.seekTo(seekToMs.coerceIn(trim.startMs, trim.endMs))
        }
    }

    // A new range pulls the playhead back inside it.
    LaunchedEffect(trim.startMs, trim.endMs) {
        if (player.currentPosition !in trim.startMs..trim.endMs) {
            player.seekTo(trim.startMs)
        }
    }

    // Report position to the timeline while playing.
    //
    // The out-point is only enforced once the player is genuinely STATE_READY: before that,
    // getCurrentPosition() can report the whole duration, which would trip the check on the
    // very first tick and stop playback instantly.
    LaunchedEffect(player, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            when (player.playbackState) {
                Player.STATE_ENDED -> {
                    // Land on the out-point so the last frame stays visible, and report the
                    // playhead there so the next play press rewinds to the in-point.
                    player.seekTo(liveTrim.endMs)
                    liveOnPosition(liveTrim.endMs)
                    liveOnPlaying(false)
                    return@LaunchedEffect
                }

                Player.STATE_READY -> {
                    val position = player.currentPosition
                    if (position >= liveTrim.endMs) {
                        // Out-point reached: stop here rather than showing discarded footage.
                        player.pause()
                        player.seekTo(liveTrim.endMs)
                        liveOnPosition(liveTrim.endMs)
                        liveOnPlaying(false)
                        return@LaunchedEffect
                    }
                    liveOnPosition(position.coerceIn(liveTrim.startMs, liveTrim.endMs))
                }

                else -> Unit
            }
            delay(POSITION_POLL_MS)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Large play affordance drawn over the preview while paused. */
@Composable
fun PlayPauseOverlay(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPlaying) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            onClick = onToggle,
            shape = AppShapes.circle,
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play preview",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}