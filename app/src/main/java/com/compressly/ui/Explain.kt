package com.compressly.ui

/**
 * Explanations for the settings that are not self-explanatory. Kept together so the wording
 * stays consistent and can be reviewed in one place.
 */
object Explain {

    val PRESET = "Presets pick a CRF, a resolution cap and an encoder speed that suit most " +
        "clips. The moment you change any of those by hand the setting switches to " +
        "Custom, so you always know exactly what you are about to encode."

    val CRF = "CRF (Constant Rate Factor) decides how much detail gets thrown away to save " +
        "space. Lower numbers keep more detail and produce bigger files; higher numbers " +
        "compress harder.\n\n" +
        "Roughly: 18 is visually lossless, 23 is a good default, 28 is noticeably " +
        "compressed, and 34+ is for the smallest possible file.\n\n" +
        "CRF does not target a fixed bitrate, so the final size still depends on how busy " +
        "the footage is."

    val RESOLUTION = "Caps the picture at a maximum height. Keeping the source's own " +
        "resolution avoids a second generation of compression and encodes much faster.\n\n" +
        "The app never upscales, because enlarging a video spends bits without adding any " +
        "detail."

    val SPEED = "How much effort x264 or x265 spends on each frame.\n\n" +
        "Faster settings finish sooner and produce slightly larger files at the same CRF. " +
        "Slower settings squeeze out more detail for the same size. " +
        "'Very fast' is a sensible default on a phone."

    val CODEC = "H.264 plays back on essentially every device.\n\n" +
        "H.265 (HEVC) is usually 30-40% smaller for the same perceived quality, but older " +
        "phones and some social apps refuse to play it. MPEG-4 Part 2 is the most compatible " +
        "of the three but compresses least efficiently."

    val AUDIO = "The audio track is re-encoded to AAC at the bitrate shown. Lower values " +
        "shrink the file further but thin out music and speech.\n\n" +
        "Turn audio off for footage you will never watch with sound."

    val ESTIMATE = "Sizes are estimated from resolution, frame rate, CRF and duration using a " +
        "bits-per-pixel model, so they are close but not exact.\n\n" +
        "Re-compressing an already-compressed clip often makes it bigger, because there is " +
        "little detail left to throw away."
}