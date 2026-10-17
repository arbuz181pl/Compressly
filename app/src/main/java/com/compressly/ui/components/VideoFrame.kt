package com.compressly.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.compressly.data.ThumbnailStrip

/**
 * Decodes and displays a single frame from [uri] at [timeMs].
 *
 * Decoding is memoised by the shared [ThumbnailStrip]; this composable only holds the result
 * for as long as it is on screen, and fades in once the bitmap arrives.
 */
@Composable
fun VideoFrame(
    uri: Uri,
    timeMs: Long,
    strip: ThumbnailStrip,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    dimmed: Boolean = false,
    targetWidthDp: Int = 320,
    targetHeightDp: Int = 200,
) {
    val density = LocalDensity.current
    val targetWidth = with(density) { targetWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { targetHeightDp.dp.roundToPx() }

    var bitmap by remember(uri, timeMs, targetWidth, targetHeight) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(uri, timeMs, targetWidth, targetHeight) {
        if (bitmap == null) {
            bitmap = strip.frame(uri, timeMs, targetHeight, targetWidth)
        }
    }

    val alpha by animateFloatAsState(
        targetValue = when {
            bitmap == null -> 0f
            dimmed -> 0.4f
            else -> 1f
        },
        label = "frameAlpha",
    )

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha),
            )
        }
    }
}

/** Poster frame for list rows and the picker grid. */
@Composable
fun VideoPoster(
    uri: Uri,
    strip: ThumbnailStrip,
    modifier: Modifier = Modifier,
) = VideoFrame(
    uri = uri,
    timeMs = 0L,
    strip = strip,
    modifier = modifier,
    contentScale = ContentScale.Crop,
)
