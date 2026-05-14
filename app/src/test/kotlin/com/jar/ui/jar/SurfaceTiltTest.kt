package com.jar.ui.jar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `surfaceTilt` returns the (leftY, rightY) endpoints of the liquid's top edge given:
 *  - jarBodyWidth: width of the jar body (px)
 *  - centerY: where a flat (untilted) surface would sit (px from the canvas top)
 *  - tilt: phone's x-axis gravity normalised to [-1, +1] where +1 means strong right-tilt
 *
 * Convention: positive tilt → liquid pools to the LEFT side (because phone tilts right and
 * the world's gravity vector rotates relative to the device). So leftY drops (toward the jar
 * bottom = larger Y), rightY rises (smaller Y).
 *
 * Tilt magnitude is clamped to ±0.45 (~25° equivalent) so the surface never lays itself flat
 * against the side wall and the visual stays reasonable.
 */
class SurfaceTiltTest {

    private val width = 200f
    private val centerY = 100f
    private val maxOffset = width * 0.5f * 0.45f  // 45 px at width=200, ±0.45 clamp

    @Test fun zeroTiltKeepsSurfaceFlat() {
        val (l, r) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = 0f)
        assertEquals(centerY, l, 0.01f)
        assertEquals(centerY, r, 0.01f)
    }

    @Test fun positiveTiltLowersLeftRaisesRight() {
        val (l, r) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = 0.5f)
        // 0.5 clamped to 0.45 → offset = maxOffset
        assertEquals(centerY + maxOffset, l, 0.01f)
        assertEquals(centerY - maxOffset, r, 0.01f)
    }

    @Test fun negativeTiltLowersRightRaisesLeft() {
        val (l, r) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = -0.5f)
        assertEquals(centerY - maxOffset, l, 0.01f)
        assertEquals(centerY + maxOffset, r, 0.01f)
    }

    @Test fun moderateTiltScalesProportionally() {
        // 0.225 = half the clamp; offset should be half of maxOffset.
        val (l, r) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = 0.225f)
        assertEquals(centerY + maxOffset / 2f, l, 0.01f)
        assertEquals(centerY - maxOffset / 2f, r, 0.01f)
    }

    @Test fun extremeTiltClampedToMaxOffset() {
        val (l1, r1) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = 5f)
        val (l2, r2) = surfaceTilt(jarBodyWidth = width, centerY = centerY, tilt = 0.45f)
        assertEquals(l2, l1, 0.01f)
        assertEquals(r2, r1, 0.01f)
    }
}
