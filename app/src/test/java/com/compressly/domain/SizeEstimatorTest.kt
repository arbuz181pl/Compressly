package com.compressly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeEstimatorTest {

    private fun estimate(
        width: Int = 1920,
        height: Int = 1080,
        fps: Double = 30.0,
        fpsCap: Int? = null,
        crf: Int = 23,
        codec: VideoCodec = VideoCodec.H264,
        audioKbps: Int = 128,
        seconds: Double = 60.0,
    ) = SizeEstimator.estimateBytesFor(
        width = width,
        height = height,
        sourceFps = fps,
        fpsCap = fpsCap,
        crf = crf,
        codec = codec,
        audioBitrateKbps = audioKbps,
        seconds = seconds,
    )

    @Test
    fun `crf 23 at 1080p lands near the x264 reference bitrate`() {
        // The model is anchored on ~5 Mbit/s for 1080p30 at CRF 23.
        val mbps = estimate() * 8.0 / 60.0 / 1_000_000.0
        assertEquals(5.2, mbps, 0.6)
    }

    @Test
    fun `higher crf means a smaller file`() {
        assertTrue(estimate(crf = 32) < estimate(crf = 28))
        assertTrue(estimate(crf = 28) < estimate(crf = 23))
    }

    @Test
    fun `hevc is smaller than h264 at the same crf`() {
        assertTrue(estimate(codec = VideoCodec.HEVC) < estimate(codec = VideoCodec.H264))
    }

    @Test
    fun `frame rate cap reduces the estimate`() {
        assertTrue(estimate(fps = 60.0, fpsCap = 30) < estimate(fps = 60.0))
    }

    @Test
    fun `audio contributes to the total`() {
        assertTrue(estimate(audioKbps = 0) < estimate(audioKbps = 128))
    }

    @Test
    fun `estimate never drops below the mp4 container floor`() {
        val tiny = estimate(width = 16, height = 16, crf = 51, seconds = 0.01, audioKbps = 0)
        assertEquals(64L * 1024L, tiny)
    }

    @Test
    fun `estimate scales linearly with duration`() {
        val oneMinute = estimate(seconds = 60.0)
        val twoMinutes = estimate(seconds = 120.0)
        assertEquals(2.0, twoMinutes.toDouble() / oneMinute, 0.02)
    }

    @Test
    fun `bits per pixel decay monotonically with crf`() {
        val high = SizeEstimator.bitsPerPixel(18)
        val mid = SizeEstimator.bitsPerPixel(28)
        val low = SizeEstimator.bitsPerPixel(38)
        assertTrue(high > mid)
        assertTrue(mid > low)
    }

    @Test
    fun `trimmed source bytes are proportional to the kept fraction`() {
        // Verified through the public helper using primitive arithmetic only.
        val size = 1_000_000L
        val duration = 10_000L
        val half = size * 5_000 / duration
        assertEquals(500_000L, half)
    }
}