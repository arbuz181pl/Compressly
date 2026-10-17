package com.compressly.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts filmstrip frames for the trim timeline.
 *
 * Frames are decoded off the main thread and cached by `(uri, timeMs, width)` in a
 * size-bounded LRU so scrolling the timeline does not re-decode.
 */
class ThumbnailStrip(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * Decodes [count] evenly spaced frames across `[startMs, endMs]`, at [heightPx] tall.
     * Returns a list of [count] entries, where a null entry means that frame could not be
     * decoded (corrupt file, unsupported codec, seek past the end).
     */
    suspend fun frames(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        count: Int,
        heightPx: Int,
        widthPx: Int,
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val span = (endMs - startMs).coerceAtLeast(1L)
        (0 until count).map { index ->
            val timeMs = startMs + span * index / count.coerceAtLeast(1)
            frame(uri, timeMs, heightPx, widthPx)
        }
    }

    suspend fun frame(
        uri: Uri,
        timeMs: Long,
        heightPx: Int,
        widthPx: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = key(uri, timeMs, heightPx, widthPx)
        cache.get(key)?.let { return@withContext it }

        val bitmap = decode(uri, timeMs, heightPx, widthPx) ?: return@withContext null
        cache.put(key, bitmap)
        bitmap
    }

    private fun key(uri: Uri, timeMs: Long, heightPx: Int, widthPx: Int) =
        "$uri|$timeMs|$heightPx|$widthPx"

    private fun decode(
        uri: Uri,
        timeMs: Long,
        heightPx: Int,
        widthPx: Int,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Scaled decode avoids allocating a full 4K frame just to shrink it.
                retriever.getScaledFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    widthPx,
                    heightPx,
                )
            } else {
                retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { full ->
                        Bitmap.createScaledBitmap(full, widthPx, heightPx, true).also {
                            if (it != full) full.recycle()
                        }
                    }
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Single poster frame, used by the picker grid and queue rows. */
    suspend fun poster(uri: Uri, widthPx: Int, heightPx: Int): Bitmap? =
        frame(uri, 0L, heightPx, widthPx)

    fun evict(uri: Uri) {
        cache.snapshot().keys
            .filter { it.startsWith(uri.toString()) }
            .forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()

    private companion object {
        // Roughly 24 screens' worth of small filmstrip thumbnails.
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
