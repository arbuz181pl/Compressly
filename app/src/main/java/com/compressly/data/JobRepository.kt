package com.compressly.data

import com.compressly.domain.CompressJob
import com.compressly.domain.CompressResult
import com.compressly.domain.EncoderSettings
import com.compressly.domain.JobStatus
import com.compressly.domain.OutputNaming
import com.compressly.domain.TrimRange
import com.compressly.domain.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Single source of truth for the batch queue.
 *
 * Held as a process-wide singleton so the UI and the foreground service observe the same
 * state; jobs survive configuration changes and the service outliving the activity.
 */
object JobRepository {

    private val _jobs = MutableStateFlow<List<CompressJob>>(emptyList())
    val jobs: StateFlow<List<CompressJob>> = _jobs.asStateFlow()

    fun enqueue(
        video: VideoInfo,
        trim: TrimRange,
        settings: EncoderSettings,
        trimmedSourceBytes: Long,
        outputName: String = OutputNaming.sanitize(
            OutputNaming.defaultFor(video.displayName) + "-compressed",
            video.displayName,
        ),
    ): CompressJob {
        val job = CompressJob(
            id = UUID.randomUUID().toString(),
            video = video,
            trim = trim,
            settings = settings,
            outputName = outputName,
            trimmedSourceBytes = trimmedSourceBytes,
        )
        _jobs.update { it + job }
        return job
    }

    fun nextQueued(): CompressJob? = _jobs.value.firstOrNull { it.status == JobStatus.QUEUED }

    fun mutate(id: String, transform: (CompressJob) -> CompressJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun markRunning(id: String) = mutate(id) { it.copy(status = JobStatus.RUNNING, progress = 0f) }

    fun markProgress(id: String, fraction: Float, speed: Double, etaSeconds: Long?) =
        mutate(id) {
            it.copy(
                progress = fraction.coerceIn(0f, 1f),
                speed = speed,
                etaSeconds = etaSeconds,
            )
        }

    fun markCompleted(id: String, result: CompressResult) = mutate(id) {
        it.copy(status = JobStatus.COMPLETED, progress = 1f, result = result, etaSeconds = 0L)
    }

    fun markFailed(id: String, error: String) =
        mutate(id) { it.copy(status = JobStatus.FAILED, error = error, etaSeconds = null) }

    fun markCancelled(id: String) =
        mutate(id) { it.copy(status = JobStatus.CANCELLED, etaSeconds = null) }

    /** Removes a single terminal job from the list. */
    fun dismiss(id: String) {
        _jobs.update { list -> list.filterNot { it.id == id && it.status.isTerminal } }
    }

    /** Clears finished/failed/cancelled jobs, leaving anything still in flight. */
    fun clearFinished() {
        _jobs.update { list -> list.filterNot { it.status.isTerminal } }
    }

    fun hasWork(): Boolean = _jobs.value.any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }

    /** Aggregate bytes saved across every completed job. */
    fun totalSavedBytes(): Long = _jobs.value.sumOf { if (it.status == JobStatus.COMPLETED) it.savedBytes else 0L }
}
