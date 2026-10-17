package com.compressly.ui

import android.net.Uri
import com.compressly.domain.CompressJob
import com.compressly.domain.CompressionPreset
import com.compressly.domain.EncoderSettings
import com.compressly.domain.ResolutionOption
import com.compressly.domain.SpeedPreset
import com.compressly.domain.TrimRange
import com.compressly.domain.VideoCodec
import com.compressly.domain.VideoInfo

enum class Screen { HOME, PICKER, EDITOR, QUEUE }

data class CompresslyUiState(
    val screen: Screen = Screen.HOME,

    // Library
    val library: List<VideoInfo> = emptyList(),
    val libraryLoading: Boolean = false,
    val selection: Set<Uri> = emptySet(),

    // Permissions
    val hasVideoPermission: Boolean = false,
    val notificationsBlocked: Boolean = false,

    // Editor
    val editorVideo: VideoInfo? = null,
    val trim: TrimRange = TrimRange(0L, 0L),
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val settings: EncoderSettings = EncoderSettings(),
    val activePreset: CompressionPreset = CompressionPreset.BALANCED,
    val estimatedBytes: Long = 0L,
    val editorFrameRate: Double = 30.0,
    /** Editable output filename, without the `.mp4` suffix. */
    val outputName: String = "",
    /** Set when the device cannot play this file; the editor falls back to a still frame. */
    val previewFailed: Boolean = false,

    // Queue
    val jobs: List<CompressJob> = emptyList(),

    val engineVersion: String = "",
) {
    val editorDurationMs: Long get() = trim.durationMs

    val selectedVideos: List<VideoInfo>
        get() = library.filter { it.uri in selection }

    val runningCount: Int
        get() = jobs.count { !it.status.isTerminal }

    val hasQueue: Boolean get() = jobs.isNotEmpty()

    val canOpenEditor: Boolean get() = editorVideo != null
}

val DefaultSettings = EncoderSettings(
    codec = VideoCodec.H264,
    crf = 28,
    resolution = ResolutionOption.FHD,
    speed = SpeedPreset.VERYFAST,
    audioBitrateKbps = 128,
    keepAudio = true,
    fpsCap = null,
)
