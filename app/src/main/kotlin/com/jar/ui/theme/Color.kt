package com.jar.ui.theme

import androidx.compose.ui.graphics.Color
import com.jar.data.JarState

val CalmGreen = Color(0xFF2E8B57)

/**
 * Brighter than the muted [Amber] used elsewhere — this yellow needs to read clearly through
 * the jar's translucent liquid layer at small sizes. Amber tends to look brown when stacked
 * with the glass outline.
 */
val JarYellow = Color(0xFFF5C518)
val Amber = Color(0xFFE5A400)
val WarningRed = Color(0xFFCC3B3B)

val LightSurface = Color(0xFFFAFAFA)
val DarkSurface = Color(0xFF111111)
val LightOnSurface = Color(0xFF111111)
val DarkOnSurface = Color(0xFFF5F5F5)

/**
 * Maps a [JarState] to the jar's accent color (design 2026-05-12):
 *  - 0.70..1.00 remaining → green
 *  - 0.30..0.70 remaining → yellow
 *  - 0.00..0.30 remaining → red
 *  - overdrawn (negative balance) → red, regardless of fraction
 */
fun jarAccent(state: JarState): Color = when {
    state.isOverdrawn -> WarningRed
    state.fractionRemaining < 0.30f -> WarningRed
    state.fractionRemaining < 0.70f -> JarYellow
    else -> CalmGreen
}
