package com.jar.ui.theme

import androidx.compose.ui.graphics.Color
import com.jar.data.JarState

val CalmGreen = Color(0xFF2E8B57)
val Amber = Color(0xFFE5A400)
val WarningRed = Color(0xFFCC3B3B)

val LightSurface = Color(0xFFFAFAFA)
val DarkSurface = Color(0xFF111111)
val LightOnSurface = Color(0xFF111111)
val DarkOnSurface = Color(0xFFF5F5F5)

/**
 * Maps a [JarState] to the jar's accent color per spec §8.3:
 * overdrawn or < 20% left → red, < 40% left → amber, otherwise green.
 */
fun jarAccent(state: JarState): Color = when {
    state.isOverdrawn -> WarningRed
    state.fractionRemaining < 0.20f -> WarningRed
    state.fractionRemaining < 0.40f -> Amber
    else -> CalmGreen
}
