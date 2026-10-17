package com.compressly.core

import java.util.Locale
import kotlin.math.roundToInt

object Format {

    fun bytes(value: Long): String {
        if (value < 1000) return "$value B"
        val units = listOf("kB", "MB", "GB", "TB")
        var v = value.toDouble() / 1000.0
        var unit = 0
        while (v >= 1000 && unit < units.lastIndex) {
            v /= 1000.0
            unit++
        }
        return if (v >= 100) "${v.roundToInt()} ${units[unit]}"
        else "${(v * 10).roundToInt() / 10.0} ${units[unit]}"
    }

    fun duration(ms: Long): String {
        val safe = if (ms < 0) 0L else ms
        val totalSeconds = safe / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Timecode with a tenth of a second, used while scrubbing. */
    fun preciseDuration(ms: Long): String {
        val safe = if (ms < 0) 0L else ms
        val totalSeconds = safe / 1000
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val tenths = ((safe % 1000) / 100f).roundToInt().coerceIn(0, 9)
        return if (totalSeconds >= 3600) {
            String.format(Locale.ROOT, "%d:%02d:%02d.%d", totalSeconds / 3600, m, s, tenths)
        } else {
            String.format(Locale.ROOT, "%d:%02d.%d", m, s, tenths)
        }
    }

    fun eta(seconds: Long?): String {
        if (seconds == null || seconds < 0) return "--"
        if (seconds < 60) return "${seconds}s left"
        val m = seconds / 60
        val s = seconds % 60
        return if (m < 60) "${m}m ${s}s left" else "${m / 60}h ${m % 60}m left"
    }

    fun speed(value: Double): String =
        if (value <= 0.0) "--" else String.format(Locale.ROOT, "%.2fx", value)

    fun percent(fraction: Float): String =
        if (fraction <= 0f) "0%" else "${(fraction * 100).roundToInt().coerceIn(0, 100)}%"

    fun saved(fraction: Float): String {
        if (fraction <= 0f) return "No change"
        val pct = (fraction * 100).roundToInt().coerceIn(0, 999)
        return "$pct% smaller"
    }
}
