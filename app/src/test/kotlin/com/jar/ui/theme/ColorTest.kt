package com.jar.ui.theme

import com.jar.data.JarState
import org.junit.Assert.assertEquals
import org.junit.Test

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

    @Test fun halfJarIsGreen() {
        assertEquals(CalmGreen, jarAccent(state(0.5f)))
    }

    @Test fun below40PctIsAmber() {
        assertEquals(Amber, jarAccent(state(0.39f)))
    }

    @Test fun below20PctIsRed() {
        assertEquals(WarningRed, jarAccent(state(0.15f)))
    }

    @Test fun overdrawnIsRedEvenIfFractionNonZero() {
        assertEquals(WarningRed, jarAccent(state(0.5f, overdrawn = true)))
    }

    @Test fun emptyJarIsRed() {
        assertEquals(WarningRed, jarAccent(state(0f)))
    }
}
