package com.compressly.domain

/**
 * Normalises a user-supplied output filename before it reaches MediaStore.
 *
 * MediaStore takes the display name verbatim, so separators and control characters have to
 * go or the entry becomes invisible in the gallery and awkward to share.
 */
object OutputNaming {

    private const val MAX_LENGTH = 120

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    /** Containers we replace rather than treat as part of the name. */
    private val VIDEO_EXTENSIONS = listOf(
        ".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v",
        ".3gp", ".mpeg", ".mpg", ".wmv", ".flv", ".ts",
    )

    /** Name shown in the editor before compressing. */
    fun defaultFor(displayName: String): String {
        val base = displayName.substringBeforeLast('.', displayName).trim()
            .ifBlank { "video" }
        return base
    }

    /**
     * Sanitises [raw] for use as a MediaStore display name, guaranteeing a `.mp4` extension.
     * Falls back to a name derived from [fallbackSource] when nothing usable survives.
     */
    fun sanitize(raw: String, fallbackSource: String): String {
        val fallback = "${defaultFor(fallbackSource)}-compressed.mp4"

        val cleaned = ILLEGAL.replace(raw, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ' ')

        if (cleaned.isBlank()) return fallback

        // Only a recognised video container is replaced. Anything else the user typed is
        // part of the name, so "my.video.v2" keeps its dots.
        val stripped = VIDEO_EXTENSIONS
            .firstOrNull { cleaned.endsWith(it, ignoreCase = true) }
            ?.let { cleaned.dropLast(it.length) }
            ?: cleaned

        val stem = stripped.trim().trim('.', ' ')

        // A name that reduced to nothing, or to a bare extension word, is not usable.
        if (stem.isBlank()) return fallback
        if (VIDEO_EXTENSIONS.any { it.removePrefix(".").equals(stem, ignoreCase = true) }) {
            return fallback
        }

        return if (stem.length + EXTENSION.length <= MAX_LENGTH) {
            stem + EXTENSION
        } else {
            // Truncate the stem, never the extension.
            stem.take(MAX_LENGTH - EXTENSION.length).trimEnd() + EXTENSION
        }
    }

    private const val EXTENSION = ".mp4"
}