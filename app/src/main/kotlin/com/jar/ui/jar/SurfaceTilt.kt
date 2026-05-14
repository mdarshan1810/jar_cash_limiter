package com.jar.ui.jar

import kotlin.math.abs

/** Maximum tilt magnitude we honour, in normalised units. ≈ 25° equivalent. */
private const val TILT_CLAMP = 0.45f

/** What fraction of half the body width the surface can rise/fall on each side at max tilt. */
private const val MAX_OFFSET_FRACTION = 0.5f

/**
 * Pure geometry helper. Given the jar body width and the Y position of an untilted (flat)
 * liquid surface, returns the (leftY, rightY) endpoints of the surface when the device is
 * tilted along its X axis.
 *
 * @param jarBodyWidth pixels across the jar body at the liquid line
 * @param centerY      Y coordinate of the flat-water surface
 * @param tilt         normalized x-axis gravity in [-1, +1]; positive = right-tilt
 *                     (liquid pools on the LEFT, so leftY grows, rightY shrinks)
 */
internal fun surfaceTilt(
    jarBodyWidth: Float,
    centerY: Float,
    tilt: Float
): Pair<Float, Float> {
    val clamped = tilt.coerceIn(-TILT_CLAMP, TILT_CLAMP)
    // sign(tilt) sets the direction; abs preserves magnitude.
    val maxOffset = jarBodyWidth * MAX_OFFSET_FRACTION * TILT_CLAMP
    val offset = (clamped / TILT_CLAMP) * maxOffset
    return Pair(centerY + offset, centerY - offset)
}

/** Exposed for tests that want to assert the maximum offset directly. */
internal fun maxSurfaceOffset(jarBodyWidth: Float): Float =
    jarBodyWidth * MAX_OFFSET_FRACTION * TILT_CLAMP

@Suppress("unused") // kept around for future callers that want to inspect the clamp value
internal fun normalizedTiltMagnitude(tilt: Float): Float = abs(tilt).coerceAtMost(TILT_CLAMP)
