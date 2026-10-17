package com.compressly.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates the encoded output size before the job runs.
 *
 * CRF does not specify a bitrate, so we model it as bits-per-pixel-per-frame. The curve is
 * anchored on x264 measurements for 1080p30 (CRF 23 lands near 5 Mbit/s, i.e. ~0.080 bpp)
 * and decays by a factor of two per 6 CRF steps.
 */
object SizeEstimator {

    private const val BPP_AT_CRF_23 = 0.080
    private const val ASSUMED_FPS = 30.0

    fun bitsPerPixel(crf: Int): Double =
        BPP_AT_CRF_23 * 2.0.pow((23 - crf) / 6.0)

    /** Output pixel dimensions after applying [EncoderSettings.resolution]. */
    fun targetSize(source: VideoInfo, settings: EncoderSettings): Pair<Int, Int> {
        val w = max(2, source.width)
        val h = max(2, source.height)
        val cap = settings.resolution.maxHeight
            ?: return w to h
        if (h <= cap) return w to h
        // Cap on the long edge, then force even numbers for yuv420p.
        val scale = cap.toDouble() / h
        val outH = (cap / 2) * 2
        val outW = max(2, (w * scale).roundToInt() / 2 * 2)
        return outW to outH
    }

    /** Estimated output size in bytes for the trimmed clip. */
    fun estimateBytes(
        source: VideoInfo,
        trim: TrimRange,
        settings: EncoderSettings,
        sourceFps: Double = ASSUMED_FPS,
    ): Long {
        val (w, h) = targetSize(source, settings)
        return estimateBytesFor(
            width = w,
            height = h,
            sourceFps = sourceFps,
            fpsCap = settings.fpsCap,
            crf = settings.crf,
            codec = settings.codec,
            audioBitrateKbps = if (settings.keepAudio && source.hasAudio) settings.audioBitrateKbps else 0,
            seconds = trim.durationMs / 1000.0,
        )
    }

    /**
     * Pure size model, kept free of Android types so it can be unit tested.
     */
    fun estimateBytesFor(
        width: Int,
        height: Int,
        sourceFps: Double,
        fpsCap: Int?,
        crf: Int,
        codec: VideoCodec,
        audioBitrateKbps: Int,
        seconds: Double,
    ): Long {
        val fps = min(sourceFps, (fpsCap ?: sourceFps).toDouble()).coerceAtLeast(1.0)

        // HEVC is roughly 30-40% smaller than H.264 at equal perceptual quality.
        val codecFactor = when (codec) {
            VideoCodec.H264 -> 1.0
            VideoCodec.HEVC -> 0.62
            VideoCodec.MPEG4 -> 1.25
        }

        val videoBitrate = width.toDouble() * height * fps * bitsPerPixel(crf) * codecFactor
        val audioBitrate = audioBitrateKbps * 1000.0
        val bytes = ((videoBitrate + audioBitrate) * seconds / 8.0)
        return bytes.toLong().coerceAtLeast(64L * 1024L)
    }

    /**
     * Source bytes that correspond to the trimmed range, so savings compare like with like.
     */
    fun trimmedSourceBytes(source: VideoInfo, trim: TrimRange): Long {
        if (source.durationMs <= 0L || source.sizeBytes <= 0L) return source.sizeBytes
        val fraction = (trim.durationMs.toDouble() / source.durationMs).coerceIn(0.0, 1.0)
        return (source.sizeBytes * fraction).toLong()
    }
}
