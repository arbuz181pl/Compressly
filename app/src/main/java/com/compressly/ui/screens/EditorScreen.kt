package com.compressly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compressly.ui.theme.AppShapes
import com.compressly.core.Format
import com.compressly.data.ThumbnailStrip
import com.compressly.domain.CompressionPreset
import com.compressly.domain.EncoderSettings
import com.compressly.domain.ResolutionOption
import com.compressly.domain.SpeedPreset
import com.compressly.domain.TrimRange
import com.compressly.domain.VideoCodec
import com.compressly.domain.VideoInfo
import com.compressly.ui.CompresslyUiState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.compressly.ui.Explain
import com.compressly.ui.components.InfoTip
import com.compressly.ui.components.PlayPauseOverlay
import com.compressly.ui.components.VideoPreview
import com.compressly.ui.components.TrimTimeline
import com.compressly.ui.components.VideoFrame

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    state: CompresslyUiState,
    thumbnails: ThumbnailStrip,
    onBack: () -> Unit,
    onTrimChange: (TrimRange) -> Unit,
    onScrub: (Long) -> Unit,
    onPreset: (CompressionPreset) -> Unit,
    onSettingsChange: ((EncoderSettings) -> EncoderSettings) -> Unit,
    onNameChange: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onPreviewFailed: () -> Unit,
    onQueue: () -> Unit,
) {
    val video = state.editorVideo ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video.shortName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQueue,
                modifier = Modifier.navigationBarsPadding(),
                shape = AppShapes.pill,
                expanded = true,
                icon = { Icon(Icons.Filled.Compress, contentDescription = null) },
                text = { Text("Compress") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(AppShapes.cardLarge)
                    .clickable(onClick = onTogglePlay),
            ) {
                if (state.previewFailed) {
                    // Playback unsupported for this codec: fall back to a still frame.
                    VideoFrame(
                        uri = video.uri,
                        timeMs = state.playheadMs,
                        strip = thumbnails,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        targetWidthDp = 640,
                        targetHeightDp = 360,
                    )
                } else {
                    VideoPreview(
                        uri = video.uri,
                        trim = state.trim,
                        isPlaying = state.isPlaying,
                        seekToMs = state.playheadMs,
                        onPositionChange = onScrub,
                        onPlayingChange = onPlayingChange,
                        onFailure = onPreviewFailed,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PlayPauseOverlay(
                    isPlaying = state.isPlaying || state.previewFailed,
                    onToggle = onTogglePlay,
                    modifier = Modifier.fillMaxSize(),
                )

                // Time readout, so the preview and the trim values never disagree.
                Surface(
                    shape = AppShapes.pill,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.scrim
                        .copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        Format.preciseDuration(state.playheadMs),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // ---- Trim -------------------------------------------------------------
            SectionTitle("Trim")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel("In", state.trim.startMs)
                TimeLabel("Out", state.trim.endMs)
                TimeLabel("Length", state.trim.durationMs, emphasise = true)
            }

            TrimTimeline(
                uri = video.uri,
                totalDurationMs = video.durationMs,
                trim = state.trim,
                playheadMs = state.playheadMs,
                strip = thumbnails,
                onTrimChange = onTrimChange,
                onScrub = onScrub,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        onTrimChange(TrimRange(0L, video.durationMs.coerceAtMost(15_000L)))
                    },
                    label = { Text("First 15s") },
                )
                AssistChip(
                    onClick = { onTrimChange(TrimRange.full(video.durationMs)) },
                    label = { Text("Whole clip") },
                )
            }

            HorizontalDivider()

            // ---- Preset -----------------------------------------------------------
            SectionTitle("Preset", Explain.PRESET)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CompressionPreset.entries.forEachIndexed { index, preset ->
                    SegmentedButton(
                        selected = state.activePreset == preset,
                        onClick = { onPreset(preset) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = CompressionPreset.entries.size,
                        ),
                    ) {
                        Text(preset.label, maxLines = 1)
                    }
                }
            }
            Text(
                state.activePreset.blurb,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )

            // ---- Quality ----------------------------------------------------------
            SectionTitle("Quality", Explain.CRF)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("CRF", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    InfoTip(title = "CRF", text = Explain.CRF)
                }
                Text(
                    "${state.settings.crf}",
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = state.settings.crf.toFloat(),
                onValueChange = { value -> onSettingsChange { current -> current.copy(crf = value.toInt()) } },
                valueRange = 16f..38f,
                steps = 21,
            )
            Text(
                qualityHint(state.settings.crf),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )

            // ---- Encoder options --------------------------------------------------
            SectionTitle("Encoder")
            OptionDropdown(
                label = "Codec",
                value = state.settings.codec.label,
                options = VideoCodec.entries,
                optionLabel = { it.label },
                onSelect = { codec -> onSettingsChange { current -> current.copy(codec = codec) } },
                help = Explain.CODEC,
            )
            OptionDropdown(
                label = "Resolution",
                value = state.settings.resolution.label,
                options = ResolutionOption.entries,
                optionLabel = { it.label },
                // Never offer a cap larger than the source; upscaling only wastes bytes.
                enabledOptions = ResolutionOption.entries.filter { candidate ->
                    candidate.maxHeight == null || video.height <= candidate.maxHeight
                },
                onSelect = { option -> onSettingsChange { current -> current.copy(resolution = option) } },
                help = Explain.RESOLUTION,
            )
            OptionDropdown(
                label = "Speed",
                value = state.settings.speed.label,
                options = SpeedPreset.entries,
                optionLabel = { it.label },
                onSelect = { speed -> onSettingsChange { current -> current.copy(speed = speed) } },
                help = Explain.SPEED,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Keep audio",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        InfoTip(title = "Audio", text = Explain.AUDIO)
                    }
                    Text(
                        "${state.settings.audioBitrateKbps} kbps AAC",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.settings.keepAudio,
                    onCheckedChange = { keep -> onSettingsChange { current -> current.copy(keepAudio = keep) } },
                )
            }

            HorizontalDivider()

            // ---- Output name -----------------------------------------------------
            SectionTitle("File name")
            OutlinedTextField(
                value = state.outputName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Saved as") },
                supportingText = { Text("Always saved as .mp4 in Movies/Compressly") },
                trailingIcon = {
                    if (state.outputName.isNotEmpty()) {
                        IconButton(onClick = { onNameChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear name")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            HorizontalDivider()

            // ---- Result -----------------------------------------------------------
            EstimateCard(
                state = state,
                video = video,
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, help: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (help != null) {
            InfoTip(title = text, text = help)
        }
    }
}

@Composable
private fun TimeLabel(label: String, ms: Long, emphasise: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Text(
            Format.preciseDuration(ms),
            style = if (emphasise) {
                androidx.compose.material3.MaterialTheme.typography.titleMedium
            } else {
                androidx.compose.material3.MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun qualityHint(crf: Int): String = when {
    crf <= 20 -> "Visually lossless \u00B7 large file"
    crf <= 24 -> "High quality"
    crf <= 28 -> "Good quality \u00B7 recommended"
    crf <= 32 -> "Noticeable compression"
    else -> "Very small file \u00B7 visible artefacts"
}

@Composable
private fun <T> OptionDropdown(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    help: String? = null,
    enabledOptions: List<T> = options,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = AppShapes.medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    if (help != null) InfoTip(title = label, text = help)
                }
                Text(
                    value,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            enabledOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EstimateCard(state: CompresslyUiState, video: VideoInfo) {
    val trimmedSource = com.compressly.domain.SizeEstimator.trimmedSourceBytes(video, state.trim)
    val savings = 1f - (state.estimatedBytes.toFloat() / trimmedSource.coerceAtLeast(1L))

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Estimated result",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    InfoTip(title = "Estimated result", text = Explain.ESTIMATE)
                }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LabelledValue("Original", Format.bytes(trimmedSource))
                LabelledValue("Estimated", Format.bytes(state.estimatedBytes))
            }
            if (savings > 0.01f) {
                Surface(
                    shape = AppShapes.pill,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "About ${Format.saved(savings)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                }
            } else if (savings < -0.02f) {
                // Re-encoding an already-compressed clip usually makes it bigger. Say so
                // rather than letting the user discover it after waiting for the encode.
                Surface(
                    shape = AppShapes.pill,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "This may make the file larger",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    "Raise the CRF, pick a smaller resolution, or pick a lighter preset.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Estimates are approximate. Actual size depends on the clip's content.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Column {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}
