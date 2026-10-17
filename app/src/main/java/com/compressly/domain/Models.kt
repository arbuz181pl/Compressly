package com.compressly.domain

import android.net.Uri

/** A video as discovered in MediaStore, with the metadata the editor needs. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val dateAddedSeconds: Long,
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 16f / 9f

    val resolutionLabel: String
        get() = if (width <= 0 || height <= 0) "Unknown" else "${height}p"

    val shortName: String
        get() = displayName.substringBeforeLast('.').take(42)
}

/** Half-open interval `[startMs, endMs)` in milliseconds. */
data class TrimRange(val startMs: Long, val endMs: Long) {

    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    fun clampedTo(durationMs: Long): TrimRange {
        val start = startMs.coerceIn(0L, durationMs)
        val end = endMs.coerceIn(start, durationMs)
        return TrimRange(start, end)
    }

    companion object {
        fun full(durationMs: Long) = TrimRange(0L, durationMs)
    }
}

enum class VideoCodec(val label: String, val shortLabel: String) {
    H264("H.264 (AVC)", "H.264"),
    HEVC("H.265 (HEVC)", "H.265"),
    MPEG4("MPEG-4 Part 2", "MP4v"),
}

/** x264/x265 speed presets, ordered fastest to slowest. */
enum class SpeedPreset(val ffmpegName: String, val label: String) {
    ULTRAFAST("ultrafast", "Fastest"),
    SUPERFAST("superfast", "Super fast"),
    VERYFAST("veryfast", "Very fast"),
    FASTER("faster", "Faster"),
    MEDIUM("medium", "Balanced"),
    SLOW("slow", "Slow"),
    SLOWER("slower", "Slower"),
    VERYSLOW("veryslow", "Slowest"),
}

enum class ResolutionOption(val label: String, val shortLabel: String, val maxHeight: Int?) {
    ORIGINAL("Keep original", "Original", null),
    UHD("2160p", "2160p", 2160),
    FHD("1080p", "1080p", 1080),
    HD("720p", "720p", 720),
    SD("480p", "480p", 480),
}

data class EncoderSettings(
    val codec: VideoCodec = VideoCodec.H264,
    /** x264/x265 quality. Lower means better quality and larger output; 18 (visually lossless) to 40. */
    val crf: Int = 28,
    val resolution: ResolutionOption = ResolutionOption.FHD,
    val speed: SpeedPreset = SpeedPreset.VERYFAST,
    val audioBitrateKbps: Int = 128,
    val keepAudio: Boolean = true,
    /** Caps the output frame rate, often the biggest size lever after CRF. */
    val fpsCap: Int? = null,
)

/** One-tap configurations, plus the Custom state the user lands in by editing anything. */
enum class CompressionPreset(
    val label: String,
    val blurb: String,
    /** False for [CUSTOM], which records hand-tuning rather than applying a preset. */
    val isPreset: Boolean = true,
) {
    EXTREME("Extreme", "Smallest file"),
    BALANCED("Balanced", "Good for sharing"),
    QUALITY("Quality", "Near-original look"),
    CUSTOM("Custom", "Tuned by hand", isPreset = false),
    ;

    /**
     * Settings this preset stands for. Resolution never upscales: the chosen cap is the
     * smallest one that still contains the source.
     */
    fun settingsFor(source: VideoInfo): EncoderSettings = when (this) {
        EXTREME -> EncoderSettings(
            codec = VideoCodec.H264,
            crf = 33,
            resolution = capFor(source.height, ResolutionOption.SD),
            speed = SpeedPreset.VERYFAST,
            audioBitrateKbps = 96,
            fpsCap = 30,
        )
        BALANCED -> EncoderSettings(
            codec = VideoCodec.H264,
            crf = 28,
            resolution = capFor(source.height, ResolutionOption.FHD),
            speed = SpeedPreset.VERYFAST,
            audioBitrateKbps = 128,
            fpsCap = null,
        )
        QUALITY -> EncoderSettings(
            codec = VideoCodec.H264,
            crf = 21,
            resolution = capFor(source.height, ResolutionOption.ORIGINAL),
            speed = SpeedPreset.MEDIUM,
            audioBitrateKbps = 192,
            fpsCap = null,
        )
        // Never applied: selecting Custom leaves the user's own settings untouched.
        CUSTOM -> EncoderSettings()
    }
}

/**
 * The resolution option matching the source's actual height: the smallest cap that still
 * contains it. Picking the largest fitting cap would label a 720p source as 2160p.
 * Falls back to ORIGINAL when the source is taller than every option.
 */
private fun capFor(sourceHeight: Int, fallback: ResolutionOption): ResolutionOption {
    if (sourceHeight <= 0) return fallback
    return ResolutionOption.entries
        .filter { it.maxHeight != null && sourceHeight <= it.maxHeight }
        .minByOrNull { it.maxHeight ?: Int.MAX_VALUE }
        ?: ResolutionOption.ORIGINAL
}

/**
 * The preset whose settings for [source] exactly match [settings], otherwise
 * [CompressionPreset.CUSTOM] once anything has been hand-tuned.
 */
fun presetMatching(source: VideoInfo, settings: EncoderSettings): CompressionPreset =
    CompressionPreset.entries.firstOrNull { it.isPreset && it.settingsFor(source) == settings }
        ?: CompressionPreset.CUSTOM

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

data class CompressResult(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val elapsedMs: Long,
)

data class CompressJob(
    val id: String,
    val video: VideoInfo,
    val trim: TrimRange,
    val settings: EncoderSettings,
    /** Sanitised display name for the output file. */
    val outputName: String = OutputNaming.sanitize(
        OutputNaming.defaultFor(video.displayName) + "-compressed",
        video.displayName,
    ),
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    /** Encoding speed as a multiple of realtime, e.g. 1.8 means 1.8x faster than playback. */
    val speed: Double = 0.0,
    val etaSeconds: Long? = null,
    val result: CompressResult? = null,
    val error: String? = null,
    /** Source byte size after trimming; the baseline for the savings calculation. */
    val trimmedSourceBytes: Long = video.sizeBytes,
) {
    val savedBytes: Long
        get() = result?.let { trimmedSourceBytes - it.sizeBytes } ?: 0L

    val savedFraction: Float
        get() = if (result == null || trimmedSourceBytes <= 0L) 0f
        else (savedBytes.toFloat() / trimmedSourceBytes).coerceIn(0f, 1f)
}
