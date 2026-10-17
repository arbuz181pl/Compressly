package com.compressly.engine

import com.compressly.domain.EncoderSettings
import com.compressly.domain.TrimRange
import com.compressly.domain.VideoCodec
import com.compressly.domain.VideoInfo
import java.util.Locale

/**
 * Builds the FFmpeg argument vector for a compression job.
 *
 * Arguments are passed to ffmpeg-kit as a `String[]` rather than a shell string, so paths
 * containing spaces, quotes or non-ASCII characters are safe without any escaping.
 */
object FFmpegArgs {

    fun build(
        inputPath: String,
        outputPath: String,
        video: VideoInfo,
        trim: TrimRange,
        settings: EncoderSettings,
    ): Array<String> {
        val args = mutableListOf<String>()

        args += listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y")

        // Accurate seeking: -ss before -i makes ffmpeg decode-and-discard up to the seek
        // point, which is both fast and frame accurate when re-encoding.
        val startSeconds = trim.startMs / 1000.0
        if (startSeconds > 0.0) {
            args += listOf("-ss", seconds(startSeconds))
        }

        args += listOf("-i", inputPath)

        val lengthSeconds = trim.durationMs / 1000.0
        if (lengthSeconds > 0.0) {
            args += listOf("-t", seconds(lengthSeconds))
        }

        // Map explicitly so subtitle/data tracks never leak into the output.
        args += listOf("-map", "0:v:0")
        if (settings.keepAudio && video.hasAudio) {
            args += listOf("-map", "0:a:0?")
        }

        val filters = buildFilterChain(video, settings)
        if (filters.isNotEmpty()) {
            args += listOf("-vf", filters)
        }

        args += buildVideoCodecArgs(settings)
        args += buildAudioArgs(settings, video)

        // Rewind metadata so the player can start rendering before the whole file is read,
        // and drop any residual rotation side data (the pixels are already upright).
        args += listOf("-metadata:s:v:0", "rotate=0")
        args += listOf("-movflags", "+faststart")

        args += outputPath
        return args.toTypedArray()
    }

    private fun buildVideoCodecArgs(settings: EncoderSettings): List<String> = buildList {
        when (settings.codec) {
            VideoCodec.H264 -> {
                add("-c:v")
                add("libx264")
                add("-preset")
                add(settings.speed.ffmpegName)
                add("-crf")
                add(settings.crf.toString())
                add("-profile:v")
                add("high")
                // 4.1 covers 1080p30 and is the safest level for broad device playback.
                add("-level")
                add("4.1")
            }
            VideoCodec.HEVC -> {
                add("-c:v")
                add("libx265")
                add("-preset")
                add(settings.speed.ffmpegName)
                add("-crf")
                add(settings.crf.toString())
                // hvc1 makes HEVC playable in more players than the hev1 default.
                add("-tag:v")
                add("hvc1")
                add("-x265-params")
                add("log-level=error")
            }
            VideoCodec.MPEG4 -> {
                add("-c:v")
                add("mpeg4")
                add("-q:v")
                add(settings.crf.toString())
            }
        }

        add("-pix_fmt")
        add("yuv420p")

        settings.fpsCap?.let {
            add("-r")
            add(it.toString())
        }
    }

    private fun buildAudioArgs(settings: EncoderSettings, video: VideoInfo): List<String> =
        if (!settings.keepAudio || !video.hasAudio) {
            listOf("-an")
        } else {
            listOf(
                "-c:a",
                "aac",
                "-b:a",
                "${settings.audioBitrateKbps}k",
                "-ac",
                "2",
                "-ar",
                "44100",
            )
        }

    private fun buildFilterChain(video: VideoInfo, settings: EncoderSettings): String {
        val parts = mutableListOf<String>()
        val cap = settings.resolution.maxHeight
        // Only shrink; scaling up would spend bytes for no visible gain.
        if (cap != null && video.height > cap) {
            val outH = (cap / 2) * 2
            if (outH >= 2) {
                // -2 lets ffmpeg derive the width from the height and keep it even,
                // which yuv420p requires.
                parts += "scale=-2:$outH"
            }
        }
        return parts.joinToString(",")
    }

    private fun seconds(value: Double): String =
        String.format(Locale.ROOT, "%.3f", value)

    /** Human readable command, used for the debug log and error messages. */
    fun describe(args: Array<String>): String =
        args.joinToString(" ") { arg ->
            if (arg.contains(' ') || arg.contains('\'')) "'" + arg.replace("'", "'\\''") + "'" else arg
        }
}
