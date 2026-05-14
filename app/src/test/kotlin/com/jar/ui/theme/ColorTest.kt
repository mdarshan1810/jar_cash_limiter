package com.jar.ui.theme

import com.jar.data.JarState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Color thresholds (per design 2026-05-12):
 *   1.00 ≥ fraction ≥ 0.70  → Green
 *   0.70 >  fraction ≥ 0.30 → Yellow
 *   0.30 >  fraction        → Red
 *   isOverdrawn             → Red (overrides everything)
 */
class ColorTest {

    private fun state(fraction: Float, overdrawn: Boolean = false): JarState = JarState(
        startingAmount = 1000L,
        spent = 0L,
        monthlyLimit = 500L,
        fractionRemaining = fraction,
        isOverdrawn = overdrawn,
        isOverLimit = false
    )

    @Test fun fullJarIsGreen() {
        assertEquals(CalmGreen, jarAccent(state(1f)))
    }

    @Test fun seventyPercentBoundaryIsGreen() {
        // Exact 0.70 still counts as green — the band is inclusive at the upper edge.
        assertEquals(CalmGreen, jarAccent(state(0.70f)))
    }

    @Test fun justBelowSeventyIsYellow() {
        assertEquals(JarYellow, jarAccent(state(0.69f)))
    }

    @Test fun halfJarIsYellow() {
        assertEquals(JarYellow, jarAccent(state(0.50f)))
    }

    @Test fun thirtyPercentBoundaryIsYellow() {
        // Exact 0.30 still counts as yellow — the lower band kicks in only below 0.30.
        assertEquals(JarYellow, jarAccent(state(0.30f)))
    }

    @Test fun justBelowThirtyIsRed() {
        assertEquals(WarningRed, jarAccent(state(0.29f)))
    }

    @Test fun fifteenPercentIsRed() {
        assertEquals(WarningRed, jarAccent(state(0.15f)))
    }

    @Test fun emptyJarIsRed() {
        assertEquals(WarningRed, jarAccent(state(0f)))
    }

    @Test fun overdrawnIsRedEvenIfFractionNonZero() {
        assertEquals(WarningRed, jarAccent(state(0.5f, overdrawn = true)))
    }
}
