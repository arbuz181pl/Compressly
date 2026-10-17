package com.compressly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compressly.ui.theme.AppShapes
import com.compressly.core.Format
import com.compressly.data.ThumbnailStrip
import com.compressly.domain.CompressJob
import com.compressly.domain.JobStatus
import com.compressly.ui.CompresslyUiState
import com.compressly.ui.components.VideoPoster

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QueueScreen(
    state: CompresslyUiState,
    thumbnails: ThumbnailStrip,
    onBack: () -> Unit,
    onDismiss: (String) -> Unit,
    onClearFinished: () -> Unit,
    onShare: (CompressJob) -> Unit,
    onDelete: (CompressJob) -> Unit,
    onDeleteMany: (List<CompressJob>) -> Unit,
) {
    val completed = state.jobs.filter { it.status == JobStatus.COMPLETED }
    val savedBytes = completed.sumOf { it.savedBytes }

    // Only finished entries can be acted on in bulk.
    val deletable = state.jobs.filter { it.status.isTerminal }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    val selectedJobs = state.jobs.filter { it.id in selectedIds }
    val selectedBytes = selectedJobs.sumOf { it.result?.sizeBytes ?: 0L }

    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (selecting) "Select" else "Queue")
                        Text(
                            if (selecting) {
                                "${selectedIds.size} of ${deletable.size} selected"
                            } else {
                                buildString {
                                    val running = state.runningCount
                                    append(if (running == 0) "Idle" else "$running active")
                                    if (savedBytes > 0) append("  \u00B7  ${Format.bytes(savedBytes)} saved")
                                }
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) exitSelection() else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selecting) "Cancel selection" else "Back",
                        )
                    }
                },
                actions = {
                    when {
                        selecting -> {
                            TextButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == deletable.size) {
                                        emptySet()
                                    } else {
                                        deletable.map { it.id }.toSet()
                                    }
                                },
                                enabled = deletable.isNotEmpty(),
                            ) { Text(if (selectedIds.size == deletable.size) "None" else "All") }
                        }

                        deletable.isNotEmpty() -> {
                            TextButton(onClick = { selecting = true }) { Text("Select") }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selecting && selectedIds.isNotEmpty()) {
                BottomAppBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildString {
                                append("${selectedIds.size} selected")
                                if (selectedBytes > 0) {
                                    append("  \u00B7  frees ${Format.bytes(selectedBytes)}")
                                }
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        )
                        FilledTonalButton(
                            onClick = {
                                onDeleteMany(selectedJobs)
                                exitSelection()
                            },
                            shape = AppShapes.pill,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.jobs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing queued yet")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.jobs, key = { it.id }) { job ->
                QueueCard(
                    job = job,
                    thumbnails = thumbnails,
                    selecting = selecting && job.status.isTerminal,
                    selected = job.id in selectedIds,
                    onToggleSelect = {
                        selectedIds = if (job.id in selectedIds) {
                            selectedIds - job.id
                        } else {
                            selectedIds + job.id
                        }
                    },
                    onShare = { onShare(job) },
                    onDelete = { onDelete(job) },
                    onDismiss = { onDismiss(job.id) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QueueCard(
    job: CompressJob,
    thumbnails: ThumbnailStrip,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectable = job.status.isTerminal

    Card(
        onClick = { if (selectable) onToggleSelect() },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.cardLarge,
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selecting && selectable) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                VideoPoster(
                    uri = job.video.uri,
                    strip = thumbnails,
                    modifier = Modifier.size(width = 92.dp, height = 58.dp),
                )
Column(Modifier.weight(1f)) {
                    Text(
                        job.video.shortName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        "${Format.duration(job.trim.durationMs)}  \u00B7  ${job.settings.codec.shortLabel}  \u00B7  ${job.settings.resolution.shortLabel}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    if (job.status == JobStatus.COMPLETED && job.result != null) {
                        Text(
                            "Saved as ${job.result.displayName}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                    }
if (job.trim.startMs > 0) {
                        Text(
                            "trimmed from ${Format.duration(job.trim.startMs)}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            when (job.status) {
                JobStatus.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${Format.percent(job.progress)}  \u00B7  ${Format.speed(job.speed)}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            Format.eta(job.etaSeconds),
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                JobStatus.COMPLETED -> {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                Format.bytes(job.result?.sizeBytes ?: 0L),
                                fontWeight = FontWeight.Bold,
                            )
                            if (job.savedFraction > 0f) {
                                Text(
                                    "${Format.saved(job.savedFraction)}  \u00B7  saved ${Format.bytes(job.savedBytes)}",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = onShare,
                                shape = AppShapes.circle,
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share")
                            }
                            FilledTonalIconButton(
                                onClick = onDelete,
                                shape = AppShapes.circle,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }

                JobStatus.FAILED -> {
                    Text(
                        job.error ?: "Failed",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }

                JobStatus.CANCELLED -> {
                    Text(
                        "Cancelled",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }

                JobStatus.QUEUED -> {
                    Text(
                        "Waiting in queue",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onDismiss) { Text("Remove") }
                }
            }
        }
    }
}
