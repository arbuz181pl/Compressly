package com.compressly.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.compressly.data.JobRepository
import com.compressly.data.MediaStoreWriter
import com.compressly.data.VideoProbe
import com.compressly.domain.CompressResult
import com.compressly.domain.JobStatus
import com.compressly.engine.EncodeOutcome
import com.compressly.engine.FFmpegArgs
import com.compressly.engine.FFmpegEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "Compressly/Service"

/**
 * Runs the batch queue as a foreground service so compression survives the app being
 * backgrounded, and drives the progress notification.
 *
 * Jobs run one at a time: parallel x264 encodes on a phone fight over the same CPU cores and
 * finish slower overall, and the progress display only tracks one item meaningfully.
 */
class CompressionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = FFmpegEngine()
    private lateinit var writer: MediaStoreWriter
    private lateinit var probe: VideoProbe

    private var drainJob: Job? = null
    private var currentJobId: String? = null

    /** Notification updates are throttled to whole percents; FFmpeg reports far more often. */
    private var lastNotifiedPercent = -1

    override fun onCreate() {
        super.onCreate()
        writer = MediaStoreWriter(applicationContext)
        probe = VideoProbe(applicationContext)
        CompressionNotifications.createChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING is a compile-time constant, so it is safe to
    // reference from API 29 even though the named field was only documented from API 35.
    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_CURRENT -> cancelCurrent()
            ACTION_CANCEL_ALL -> cancelAll()
        }

        // Promote to foreground immediately; Android allows a short grace period here.
        //
        // ServiceCompat.startForeground is deliberately avoided: as of androidx.core 1.19.1 it
        // masks the requested type with 0x40000FFF, which does not include
        // FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING (0x2000). That silently reduces the type to
        // "none" and the system throws InvalidForegroundServiceTypeException on API 35+.
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                CompressionNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else {
            startForeground(CompressionNotifications.NOTIFICATION_ID, notification)
        }

        startObserving()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        drainJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Observes the queue only to refresh the notification and to make sure a drain is
     * running. The drain itself lives in its own coroutine, otherwise collecting here would
     * cancel the loop on every progress tick.
     */
    private fun startObserving() {
        scope.launch {
            JobRepository.jobs.collect {
                notifyCurrent()
                val hasWork = JobRepository.hasWork()
                if (hasWork && drainJob?.isActive != true) {
                    drainJob = scope.launch { drainQueue() }
                } else if (!hasWork) {
                    ServiceCompat.stopForeground(
                        this@CompressionService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
            }
        }
    }

    private suspend fun drainQueue() {
        while (currentJobId == null) {
            val job = JobRepository.nextQueued() ?: return
            currentJobId = job.id
            try {
                runJob(job.id)
            } catch (t: Throwable) {
                Log.e(TAG, "Job ${job.id} crashed", t)
                JobRepository.markFailed(job.id, t.message ?: "Unexpected error")
            } finally {
                currentJobId = null
            }
        }
    }

    private suspend fun runJob(jobId: String) {
        val job = JobRepository.jobs.value.firstOrNull { it.id == jobId } ?: return

        JobRepository.markRunning(jobId)

        var staged: File? = null
        var encoded: File? = null

        try {
            // 1. Stage the source so FFmpeg can seek it accurately.
            staged = writer.stageInput(jobId, job.video.uri, job.video.displayName)

            // 2. Encode to a private temp file. The MediaStore entry is only created once
            //    the encode succeeds, so a failure never leaves a broken row behind.
            encoded = writer.tempOutput(jobId)
            val args = FFmpegArgs.build(
                inputPath = staged.absolutePath,
                outputPath = encoded.absolutePath,
                video = job.video,
                trim = job.trim,
                settings = job.settings,
            )

            val started = System.currentTimeMillis()
            val outcome = engine.encode(args, job.trim.durationMs) { progress ->
                JobRepository.markProgress(jobId, progress.fraction, progress.speed, progress.etaSeconds)
            }

            when (outcome) {
                is EncodeOutcome.Cancelled -> {
                    JobRepository.markCancelled(jobId)
                    return
                }
                is EncodeOutcome.Failure -> {
                    Log.e(TAG, "ffmpeg failed for ${job.video.displayName}:\n${outcome.log.take(2000)}")
                    JobRepository.markFailed(jobId, friendlyError(outcome.message))
                    return
                }
                is EncodeOutcome.Success -> Unit
            }

            // 3. Read the real size before the temp file is cleaned up.
            val outputBytes = encoded.length().takeIf { it > 0 } ?: 0L
            val displayName = job.outputName
            val outputUri = writer.publish(encoded, displayName)
            val (outW, outH) = probe.dimensions(outputUri)
                ?: (job.video.width to job.video.height)

            JobRepository.markCompleted(
                jobId,
                CompressResult(
                    uri = outputUri,
                    displayName = displayName,
                    sizeBytes = outputBytes,
                    durationMs = job.trim.durationMs,
                    width = outW,
                    height = outH,
                    elapsedMs = System.currentTimeMillis() - started,
                ),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Job $jobId failed", t)
            JobRepository.markFailed(jobId, friendlyError(t.message ?: "Could not process this video"))
        } finally {
            staged?.let { writer.discard(it) }
            encoded?.let { writer.discard(it) }
        }
    }

    private fun friendlyError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "no space left" in lower -> "Not enough storage space"
            "permission denied" in lower -> "Permission denied while writing the file"
            "invalid data found" in lower -> "Unsupported or damaged video file"
            "unknown encoder" in lower -> "This device cannot encode with the selected codec"
            "decoder" in lower && "not found" in lower -> "No decoder available for this video"
            else -> raw.take(180)
        }
    }

    private fun cancelCurrent() {
        val id = currentJobId ?: return
        JobRepository.markCancelled(id)
        engine.cancelActive()
    }

    private fun cancelAll() {
        JobRepository.jobs.value
            .filter { it.status == JobStatus.QUEUED }
            .forEach { JobRepository.markCancelled(it.id) }
        currentJobId?.let {
            JobRepository.markCancelled(it)
            engine.cancelActive()
        }
    }

    private fun notifyCurrent() {
        val jobs = JobRepository.jobs.value
        val running = jobs.firstOrNull { it.status == JobStatus.RUNNING }
        val percent = ((running?.progress ?: 0f) * 100).toInt()

        // Always refresh on a status change; otherwise only when the rounded percent moves.
        val statusChanged = running?.id != lastRunningId
        lastRunningId = running?.id
        if (!statusChanged && percent == lastNotifiedPercent) return
        lastNotifiedPercent = percent

        getSystemService(NotificationManager::class.java)
            ?.notify(CompressionNotifications.NOTIFICATION_ID, buildNotification())
    }

    private var lastRunningId: String? = null

    private fun buildNotification() = CompressionNotifications.build(
        context = this,
        running = JobRepository.jobs.value.count { it.status == JobStatus.RUNNING },
        queued = JobRepository.jobs.value.count { it.status == JobStatus.QUEUED },
        currentName = JobRepository.jobs.value.firstOrNull { it.status == JobStatus.RUNNING }?.video?.shortName,
        fraction = JobRepository.jobs.value.firstOrNull { it.status == JobStatus.RUNNING }?.progress ?: 0f,
        etaSeconds = JobRepository.jobs.value.firstOrNull { it.status == JobStatus.RUNNING }?.etaSeconds,
        done = JobRepository.jobs.value.count { it.status == JobStatus.COMPLETED },
        savedBytes = JobRepository.totalSavedBytes(),
    )

    companion object {
        const val ACTION_CANCEL_CURRENT = "com.compressly.action.CANCEL_CURRENT"
        const val ACTION_CANCEL_ALL = "com.compressly.action.CANCEL_ALL"

        fun start(context: Context) {
            // minSdk is 26, so startForegroundService is always the correct call.
            context.startForegroundService(Intent(context, CompressionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompressionService::class.java))
        }
    }
}
