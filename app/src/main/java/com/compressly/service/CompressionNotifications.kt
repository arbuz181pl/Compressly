package com.compressly.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.compressly.MainActivity
import com.compressly.R
import com.compressly.core.Format
import com.compressly.data.JobRepository
import com.compressly.domain.JobStatus

/** Builds the progress notification shown while jobs are running. */
object CompressionNotifications {

    const val CHANNEL_ID = "compression"
    const val NOTIFICATION_ID = 4711

    const val ACTION_CANCEL = "com.compressly.action.CANCEL_CURRENT"
    const val ACTION_CANCEL_ALL = "com.compressly.action.CANCEL_ALL"

    private const val REQUEST_OPEN = 1
    private const val REQUEST_CANCEL = 2
    private const val REQUEST_CANCEL_ALL = 3

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Compression progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress while videos are being compressed"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        running: Int,
        queued: Int,
        currentName: String?,
        fraction: Float,
        etaSeconds: Long?,
        done: Int,
        savedBytes: Long,
    ): Notification {
        val title = when {
            done > 0 && running == 0 && queued == 0 ->
                if (savedBytes > 0) "Saved ${Format.bytes(savedBytes)}" else "Finished"

            currentName != null -> "Compressing ${currentName.take(28)}"

            else -> "Preparing to compress"
        }

        val text = when {
            running == 0 && queued > 0 -> "$queued waiting"
            else -> buildString {
                append(Format.percent(fraction))
                if (done > 0) append("  \u00B7  $done done")
                val eta = Format.eta(etaSeconds)
                if (eta != "--") append("  \u00B7  $eta")
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_compress)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(1000, (fraction * 1000).toInt().coerceIn(0, 1000), fraction <= 0f)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (running > 0) {
            builder.addAction(
                0,
                "Cancel this",
                servicePendingIntent(context, CompressionService.ACTION_CANCEL_CURRENT, REQUEST_CANCEL),
            )
        }
        if (running > 0 || queued > 0) {
            builder.addAction(
                0,
                "Cancel all",
                servicePendingIntent(context, CompressionService.ACTION_CANCEL_ALL, REQUEST_CANCEL_ALL),
            )
        }

        return builder.build()
    }

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, CompressionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Summary of the whole batch, for the queue screen header. */
    fun describeQueue(): String = run {
        val jobs = JobRepository.jobs.value
        val running = jobs.count { it.status == JobStatus.RUNNING }
        val queued = jobs.count { it.status == JobStatus.QUEUED }
        "$running running, $queued waiting"
    }
}
