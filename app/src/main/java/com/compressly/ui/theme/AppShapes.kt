package com.compressly.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shape vocabulary for the app.
 *
 * `MaterialShapes` in material3 1.5.0-beta01 is still partly internal (its shape properties
 * collide with internal parameterised factories, so Kotlin resolves to the internal ones), so
 * the expressive silhouette is expressed with stable corner shapes instead. They compose with
 * the expressive components, which accept any `Shape`.
 *
 * Interactive surfaces are symmetric: a button is curved at both ends, never squared or
 * lopsided, so the control still reads as a button from a distance.
 */
object AppShapes {

    /** Curved on both ends. The default for buttons. */
    val pill = RoundedCornerShape(percent = 50)

    /** Full circle, for icon buttons. */
    val circle = CircleShape

    /** Symmetric card, curved on all four corners. */
    val card = RoundedCornerShape(20.dp)

    /** Larger symmetric card, for result and queue entries. */
    val cardLarge = RoundedCornerShape(28.dp)

    /** Selectable row / field surface. */
    val medium = RoundedCornerShape(16.dp)

    val small = RoundedCornerShape(8.dp)
}