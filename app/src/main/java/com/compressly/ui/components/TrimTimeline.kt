package com.compressly.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.compressly.data.ThumbnailStrip
import com.compressly.domain.TrimRange
import kotlin.math.roundToLong

/** Touch target for a trim handle; deliberately far wider than the drawn bar. */
private val HANDLE_TOUCH_WIDTH = 44.dp
private val HANDLE_BAR_WIDTH = 14.dp
private const val MIN_CLIP_MS = 500L

/** Horizontal inset that lifts the handles out of the system edge-swipe areas. */
private val EDGE_INSET = 16.dp

/**
 * Filmstrip timeline with draggable in/out handles.
 *
 * Thumbnails span the *whole* clip rather than the selected range, so the handles travel over
 * a fixed strip instead of the imagery rescaling under the user's finger.
 */
@Composable
fun TrimTimeline(
    uri: Uri,
    totalDurationMs: Long,
    trim: TrimRange,
    playheadMs: Long,
    strip: ThumbnailStrip,
    onTrimChange: (TrimRange) -> Unit,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier,
    frameCount: Int = 12,
) {
    val density = LocalDensity.current
    val safeDuration = totalDurationMs.coerceAtLeast(1L)

    var frames by remember(uri, frameCount) { mutableStateOf<List<Bitmap?>>(emptyList()) }

    LaunchedEffect(uri, frameCount, safeDuration) {
        frames = strip.frames(
            uri = uri,
            startMs = 0L,
            endMs = safeDuration,
            count = frameCount,
            heightPx = with(density) { 64.dp.roundToPx() },
            widthPx = with(density) { 80.dp.roundToPx() },
        )
    }

    // The handle gestures survive recomposition and outlive a single frame of state, so the
    // callbacks must read the newest values instead of whatever they captured when the drag
    // started. Without this, every new drag re-anchors to a stale position and the handle
    // snaps back instead of moving.
    val liveTrim by rememberUpdatedState(trim)
    val liveOnTrimChange by rememberUpdatedState(onTrimChange)
    val liveOnScrub by rememberUpdatedState(onScrub)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Keep both handles clear of the system edge-back gesture zones. Without this
            // inset, the OUT handle at "end of clip" sits ~16dp from the screen edge and the
            // system steals the drag, making the handle feel dead.
            .padding(horizontal = EDGE_INSET),
    ) {
        val trackWidth: Dp = maxWidth
        val trackPx = with(density) { trackWidth.toPx() }.coerceAtLeast(1f)

        fun xToMs(x: Float): Long =
            ((x / trackPx) * safeDuration).roundToLong().coerceIn(0L, safeDuration)

        fun msToDp(ms: Long): Dp =
            with(density) { ((ms.toFloat() / safeDuration) * trackPx).toDp() }

        val startDp = msToDp(trim.startMs)
        val endDp = msToDp(trim.endMs)
        val headDp = msToDp(playheadMs)
        val scrimAlpha = 0.55f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(safeDuration, trackPx) {
                    detectTapGestures { offset ->
                        val ms = xToMs(offset.x)
                        liveOnScrub(ms.coerceIn(liveTrim.startMs, liveTrim.endMs))
                    }
                },
        ) {
            // Filmstrip
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                frames.forEach { frame ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        frame?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            // Dim everything outside the selected range.
            Box(
                Modifier
                    .width(startDp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
            Box(
                Modifier
                    .offset { IntOffset(endDp.roundToPx(), 0) }
                    .width((trackWidth - endDp).coerceAtLeast(0.dp))
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )

            TrimHandle(
                label = "IN",
                position = startDp,
                onDragTo = { x ->
                    val current = liveTrim
                    val latestEnd = current.endMs
                    val start = xToMs(x).coerceIn(0L, (latestEnd - MIN_CLIP_MS).coerceAtLeast(0L))
                    if (start != current.startMs) liveOnTrimChange(current.copy(startMs = start))
                },
            )

            TrimHandle(
                label = "OUT",
                position = endDp,
                onDragTo = { x ->
                    val current = liveTrim
                    val latestStart = current.startMs
                    val end = xToMs(x).coerceIn(
                        (latestStart + MIN_CLIP_MS).coerceAtLeast(0L),
                        safeDuration,
                    )
                    if (end != current.endMs) liveOnTrimChange(current.copy(endMs = end))
                },
            )

            // Playhead
            Box(
                Modifier
                    .offset { IntOffset(headDp.roundToPx(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * A single trim handle.
 *
 * [onDragTo] receives the handle's absolute x within the track, not a delta. The handle
 * re-anchors itself on every gesture, so drags cannot drift or accumulate stale offsets.
 */
@Composable
private fun TrimHandle(
    label: String,
    position: Dp,
    onDragTo: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var dragging by remember { mutableStateOf(false) }
    var anchorPx by remember { mutableFloatStateOf(0f) }

    val livePosition by rememberUpdatedState(position)
    val liveOnDragTo by rememberUpdatedState(onDragTo)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    position.roundToPx() - HANDLE_TOUCH_WIDTH.roundToPx() / 2,
                    0,
                )
            }
            .width(HANDLE_TOUCH_WIDTH)
            .fillMaxHeight()
            .pointerInput(Unit) {
                // Horizontal-only: the timeline sits inside a vertical scroll, and a
                // direction-agnostic gesture would lose to the scroll on mostly-vertical drags.
                detectHorizontalDragGestures(
                    onDragStart = {
                        // Re-anchor from the live position at the start of every gesture.
                        anchorPx = livePosition.toPx()
                        dragging = true
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        anchorPx += dragAmount
                        liveOnDragTo(anchorPx)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(HANDLE_BAR_WIDTH)
                .fillMaxHeight()
                .background(
                    if (dragging) colors.primary else colors.onPrimary.copy(alpha = 0.92f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (dragging) colors.onPrimary else colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 1.dp),
            )
        }
    }
}