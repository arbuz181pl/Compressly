package com.compressly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compressly.ui.theme.AppShapes
import com.compressly.core.Format
import com.compressly.domain.CompressJob
import com.compressly.domain.JobStatus
import com.compressly.ui.CompresslyUiState
import com.compressly.ui.components.VideoPoster
import com.compressly.data.ThumbnailStrip

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: CompresslyUiState,
    thumbnails: ThumbnailStrip,
    onPickVideos: () -> Unit,
    onOpenQueue: () -> Unit,
    onClearFinished: () -> Unit,
    onDismissJob: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val running = state.jobs.filter { it.status == JobStatus.RUNNING }
    val finished = state.jobs.filter { it.status.isTerminal }
    val savedBytes = state.jobs.sumOf { if (it.status == JobStatus.COMPLETED) it.savedBytes else 0L }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Compressly") },
                actions = {
                    if (state.hasQueue) {
                        TextButton(onClick = onOpenQueue) { Text("Queue") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SavingsHeadline(savedBytes = savedBytes, jobCount = finished.count { it.status == JobStatus.COMPLETED }) }

            item {
                FilledTonalButton(
                    onClick = onPickVideos,
                    shape = AppShapes.pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("Choose videos", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Text(
                    "Pick the clips you want. Batch only ever runs on what you select here.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (running.isNotEmpty()) {
                item {
                    Text(
                        "In progress",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(running, key = { it.id }) { job ->
                    JobRow(job = job, thumbnails = thumbnails, onDismiss = null)
                }
            }

            if (finished.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Finished",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = onClearFinished) { Text("Clear") }
                    }
                }
                items(finished, key = { it.id }) { job ->
                    JobRow(job = job, thumbnails = thumbnails, onDismiss = { onDismissJob(job.id) })
                }
            }

            if (state.library.isEmpty() && !state.libraryLoading) {
                item { EmptyHint() }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SavingsHeadline(savedBytes: Long, jobCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Compress,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    if (savedBytes > 0) Format.bytes(savedBytes) else "0 B",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (jobCount) {
                        0 -> "Saved so far"
                        1 -> "Saved across 1 video"
                        else -> "Saved across $jobCount videos"
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(40.dp))
        Text("No videos found on this device")
        Text(
            "Record or download a video, then come back.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun JobRow(
    job: CompressJob,
    thumbnails: ThumbnailStrip,
    onDismiss: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(job.video.shortName, maxLines = 1) },
        supportingContent = {
            Text(
                when (job.status) {
                    JobStatus.QUEUED -> "Waiting"
                    JobStatus.RUNNING -> buildString {
                        append(Format.percent(job.progress))
                        if (job.speed > 0) append("  \u00B7  ${Format.speed(job.speed)}")
                    }
                    JobStatus.COMPLETED -> buildString {
                        append(Format.bytes(job.result?.sizeBytes ?: 0L))
                        if (job.savedFraction > 0f) {
                            append("  \u00B7  ${Format.saved(job.savedFraction)}")
                        }
                    }
                    JobStatus.FAILED -> job.error ?: "Failed"
                    JobStatus.CANCELLED -> "Cancelled"
                },
                maxLines = 2,
            )
        },
        leadingContent = {
            VideoPoster(
                uri = job.video.uri,
                strip = thumbnails,
                modifier = Modifier.size(width = 72.dp, height = 44.dp),
            )
        },
        trailingContent = {
            when {
                job.status == JobStatus.COMPLETED -> Icon(Icons.Filled.Check, contentDescription = null)
                job.status == JobStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                job.status == JobStatus.RUNNING ->
                    Icon(Icons.Filled.Schedule, contentDescription = "Running")

                else -> Box(Modifier.size(24.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
