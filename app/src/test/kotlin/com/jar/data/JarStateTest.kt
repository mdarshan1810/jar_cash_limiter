package com.jar.data

import com.jar.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarStateTest {

    private fun settings(starting: Long = 3_000_000L, limit: Long = 2_000_000L): Settings =
        Settings.DEFAULT.copy(startingAmount = starting, monthlyLimit = limit)

    @Test fun freshJarIsFull() {
        val state = JarState.from(settings(), spent = 0L)
        assertEquals(1f, state.fractionRemaining, 0.0001f)
        assertFalse(state.isOverdrawn)
        assertFalse(state.isOverLimit)
    }

    @Test fun halfSpentHalfRemaining() {
        val state = JarState.from(settings(starting = 3_000_000L), spent = 1_500_000L)
        assertEquals(0.5f, state.fractionRemaining, 0.0001f)
    }

    @Test fun overdrawClampsFractionToZeroAndFlagsTrue() {
        val state = JarState.from(settings(starting = 1_000_000L), spent = 1_500_000L)
        assertEquals(0f, state.fractionRemaining, 0.0001f)
        assertTrue(state.isOverdrawn)
    }

    @Test fun exactBoundaryNotOverdrawn() {
        val state = JarState.from(settings(starting = 1_000_000L), spent = 1_000_000L)
        assertEquals(0f, state.fractionRemaining, 0.0001f)
        assertFalse(state.isOverdrawn)
    }

    @Test fun overLimitFlagsTrueIndependentOfStarting() {
        val state = JarState.from(settings(starting = 10_000_000L, limit = 500_000L), spent = 600_000L)
        assertTrue(state.isOverLimit)
        assertFalse(state.isOverdrawn)
    }

    @Test fun zeroLimitNeverFlagsOverLimit() {
        // Pre-onboarding default state: limit=0 should not spuriously trigger over-limit
        val state = JarState.from(settings(starting = 3_000_000L, limit = 0L), spent = 1_000L)
        assertFalse(state.isOverLimit)
    }

    @Test fun zeroStartingAmountYieldsZeroFraction() {
        val state = JarState.from(settings(starting = 0L), spent = 0L)
        assertEquals(0f, state.fractionRemaining, 0.0001f)
    }
}
