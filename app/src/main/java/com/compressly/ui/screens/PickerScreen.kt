package com.compressly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.compressly.ui.theme.AppShapes
import com.compressly.core.Format
import com.compressly.data.ThumbnailStrip
import com.compressly.domain.VideoInfo
import com.compressly.ui.CompresslyUiState
import com.compressly.ui.components.VideoPoster

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PickerScreen(
    state: CompresslyUiState,
    thumbnails: ThumbnailStrip,
    onBack: () -> Unit,
    onToggleSelection: (android.net.Uri) -> Unit,
    onOpenEditor: (VideoInfo) -> Unit,
    onQueueSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val selectedCount = state.selection.size
    val selectedBytes = state.selectedVideos.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose videos")
                        if (selectedCount > 0) {
                            Text(
                                "$selectedCount selected  \u00B7  ${Format.bytes(selectedBytes)}",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedCount > 0) {
                        androidx.compose.material3.TextButton(onClick = onClearSelection) {
                            Text("Clear")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                BottomAppBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${Format.bytes(selectedBytes)} selected",
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        )
                        FilledTonalButton(
                            onClick = onQueueSelected,
                            shape = AppShapes.pill,
                        ) {
                            Text("Queue $selectedCount")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 116.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.library, key = { it.uri.toString() }) { video ->
                PickerCell(
                    video = video,
                    thumbnails = thumbnails,
                    selected = video.uri in state.selection,
                    onToggleSelection = { onToggleSelection(video.uri) },
                    onOpen = { onOpenEditor(video) },
                )
            }
        }
    }
}

@Composable
private fun PickerCell(
    video: VideoInfo,
    thumbnails: ThumbnailStrip,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        shape = AppShapes.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            VideoPoster(
                uri = video.uri,
                strip = thumbnails,
                modifier = Modifier.fillMaxSize(),
            )

            // Selection checkbox sits over the poster; tapping it must not open the editor.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(AppShapes.circle),
            ) {
                androidx.compose.material3.Surface(
                    onClick = onToggleSelection,
                    shape = AppShapes.circle,
                    color = if (selected) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.45f)
                    },
                ) {
                    Box(
                        Modifier
                            .padding(5.dp)
                            .size(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Select",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Text(
                Format.duration(video.durationMs),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
        }

        Column(Modifier.padding(8.dp)) {
            Text(
                video.shortName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${video.resolutionLabel}  \u00B7  ${Format.bytes(video.sizeBytes)}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}
