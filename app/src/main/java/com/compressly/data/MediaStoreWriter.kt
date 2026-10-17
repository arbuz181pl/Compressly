package com.compressly.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Moves encoded files into the shared Movies collection and stages inputs FFmpeg can seek
 * freely.
 *
 * FFmpeg needs a seekable input for accurate `-ss` trims. SAF pipes are seek-hostile and slow,
 * so sources are copied into the cache first. That cost buys reliable trimming.
 */
class MediaStoreWriter(private val context: Context) {

    /** Cache directory for intermediates; safe to wipe at any time. */
    val workDir: File
        get() = File(context.cacheDir, "work").apply { mkdirs() }

    fun stagedInputName(jobId: String, displayName: String): String {
        val ext = displayName.substringAfterLast('.', "mp4").take(6)
        return "in_${jobId}.$ext"
    }

    /** Cache-only temp name; never shown to the user. */
    fun tempOutput(jobId: String): File = File(workDir, "out_$jobId.mp4")

    /** Copies the source into the cache so FFmpeg can seek it. Returns the file. */
    suspend fun stageInput(jobId: String, uri: Uri, displayName: String): File =
        withContext(Dispatchers.IO) {
            val target = File(workDir, stagedInputName(jobId, displayName))
            if (target.exists() && target.length() > 0) return@withContext target

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: throw IOException("Cannot read source video")

            if (target.length() == 0L) {
                target.delete()
                throw IOException("Source video is empty")
            }
            target
        }

    /**
     * Publishes [source] into Movies/Compressly and returns the resulting MediaStore uri.
     * The entry is created pending and only made visible once the bytes are fully written.
     */
    suspend fun publish(source: File, displayName: String): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = VideoLibrary.outputCollection()

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, VideoLibrary.MIME_MP4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/" + VideoLibrary.ALBUM,
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create the output entry")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: throw IOException("Could not open the output entry for writing")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            uri
        } catch (t: Throwable) {
            // Never leave a half-written, invisible entry behind.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    suspend fun deleteOutput(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    fun discard(file: File) {
        runCatching { file.delete() }
    }

    /** Removes intermediates for jobs that are no longer referenced. */
    fun clearWorkDir() {
        runCatching {
            workDir.listFiles()?.forEach { it.delete() }
        }
    }
}
