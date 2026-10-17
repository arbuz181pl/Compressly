package com.compressly.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads per-video facts MediaStore does not provide: whether the file actually carries an
 * audio track, and the frame rate.
 *
 * Frame rate feeds the size estimate, so a wrong value skews the "estimated output" figure.
 */
class VideoProbe(private val context: Context) {

    data class Details(val hasAudio: Boolean, val frameRate: Double)

    suspend fun probe(uri: Uri): Details = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val hasAudio =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val frameRate = parseFrameRate(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),
            ) ?: DEFAULT_FPS
            Details(hasAudio = hasAudio, frameRate = frameRate)
        } catch (t: Throwable) {
            // Assume audio and 30 fps: safe defaults that only make the estimate less precise.
            Details(hasAudio = true, frameRate = DEFAULT_FPS)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun parseFrameRate(raw: String?): Double? {
        val value = raw?.toDoubleOrNull() ?: return null
        return if (value.isFinite() && value > 1.0 && value <= 480.0) value else null
    }

    /** Reads the real pixel dimensions of an encoded file, so results show what shipped. */
    suspend fun dimensions(uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) w to h else null
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val DEFAULT_FPS = 30.0
    }
}
