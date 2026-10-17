package com.compressly.engine

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "Compressly/FFmpeg"

/** Progress reported while a job encodes. */
data class EncodeProgress(
    val fraction: Float,
    val speed: Double,
    val etaSeconds: Long?,
)

sealed interface EncodeOutcome {
    data class Success(val session: FFmpegSession) : EncodeOutcome
    data object Cancelled : EncodeOutcome
    data class Failure(val message: String, val log: String) : EncodeOutcome
}

/**
 * Thin coroutine wrapper over ffmpeg-kit.
 *
 * Progress comes from ffmpeg-kit's [StatisticsCallback] rather than scraping the log, so it
 * reports the real output timestamp and encoding speed.
 */
class FFmpegEngine {

    /**
     * Session id of the encode currently running, or -1 when idle. The service runs one job
     * at a time, so cancelling this session is equivalent to cancelling the current job.
     */
    @Volatile
    var activeSessionId: Long = -1L
        private set

    /** Engine banner, e.g. `8.1.1`. Useful for the about/debug screen. */
    fun version(): String = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrNull().orEmpty()

    suspend fun encode(
        args: Array<String>,
        expectedDurationMs: Long,
        onProgress: (EncodeProgress) -> Unit,
    ): EncodeOutcome {
        Log.i(TAG, "ffmpeg ${FFmpegArgs.describe(args)}")

        val result = suspendCancellableCoroutine<EncodeOutcome> { cont ->
            var session: FFmpegSession? = null

            val statsCallback = StatisticsCallback { stats: Statistics ->
                if (cont.isActive) onProgress(stats.toProgress(expectedDurationMs))
            }

            val logCallback = LogCallback { log ->
                // surface anything ffmpeg considers fatal for the failure message
                if (log.level == Level.AV_LOG_ERROR || log.level == Level.AV_LOG_FATAL) {
                    Log.w(TAG, log.message)
                }
            }

            session = FFmpegKit.executeWithArgumentsAsync(
                args,
                { completed ->
                    if (cont.isActive) {
                        val returnCode = completed.returnCode
                        val outcome: EncodeOutcome = when {
                            returnCode != null && ReturnCode.isCancel(returnCode) ->
                                EncodeOutcome.Cancelled

                            returnCode != null && ReturnCode.isSuccess(returnCode) ->
                                EncodeOutcome.Success(completed)

                            else -> {
                                // Fully explicit types: the FFmpegKit accessors are platform
                                // types and inference over them is unreliable.
                                val logs: String = completed.allLogsAsString
                                val stackTrace: String = completed.failStackTrace ?: ""
                                val firstLine: String = stackTrace.lines()
                                    .firstOrNull { line -> line.isNotBlank() }
                                    .orEmpty()
                                val message: String = firstLine.ifBlank {
                                    "FFmpeg exited with code ${returnCode?.value}"
                                }
                                EncodeOutcome.Failure(message = message, log = logs)
                            }
                        }
                        cont.resume(outcome)
                    }
                },
                logCallback,
                statsCallback,
            )

            session?.let { activeSessionId = it.sessionId }

            cont.invokeOnCancellation {
                // session may not be assigned yet if cancellation races the execute call
                session?.cancel() ?: FFmpegKit.cancel()
            }
        }

        activeSessionId = -1L
        return result
    }

    private fun Statistics.toProgress(expectedDurationMs: Long): EncodeProgress {
        val producedMs = time
        val fraction =
            if (expectedDurationMs > 0) (producedMs / expectedDurationMs).toFloat().coerceIn(0f, 1f)
            else 0f
        // speed is a multiple of realtime, so remaining output time / speed is wall clock time.
        val eta = if (speed > 0.01 && expectedDurationMs > 0) {
            (((expectedDurationMs - producedMs) / speed) / 1000.0).toLong().coerceAtLeast(0L)
        } else {
            null
        }
        return EncodeProgress(fraction = fraction, speed = speed, etaSeconds = eta)
    }

    /** Cancels the in-flight encode, if any. Safe to call when idle. */
    fun cancelActive() {
        val id = activeSessionId
        if (id >= 0) FFmpegKit.cancel(id) else FFmpegKit.cancel()
    }
}
