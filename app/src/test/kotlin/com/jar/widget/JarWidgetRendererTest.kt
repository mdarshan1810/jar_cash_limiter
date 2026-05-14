package com.jar.widget

import com.jar.data.JarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class JarWidgetRendererTest {

    private fun state(fraction: Float, overdrawn: Boolean = false) = JarState(
        startingAmount = 1000L,
        spent = 0L,
        monthlyLimit = 500L,
        fractionRemaining = fraction,
        isOverdrawn = overdrawn,
        isOverLimit = false
    )

    @Test fun rendersBitmapAtRequestedSize() {
        val bmp = JarWidgetRenderer.render(state(0.5f), widthPx = 200, heightPx = 200)
        assertEquals(200, bmp.width)
        assertEquals(200, bmp.height)
    }

    @Test fun rendersWithoutCrashAtAnyLevel() {
        // Boundary fractions should render without throwing — defensive check.
        listOf(0f, 0.29f, 0.30f, 0.69f, 0.70f, 1.0f).forEach { f ->
            val bmp = JarWidgetRenderer.render(state(f), widthPx = 150, heightPx = 200)
            assertTrue(bmp.byteCount > 0)
        }
    }

    @Test fun overdrawnRendersWithoutCrash() {
        val bmp = JarWidgetRenderer.render(state(0.5f, overdrawn = true), 150, 200)
        assertTrue(bmp.byteCount > 0)
    }

    @Test fun rejectsZeroOrNegativeSize() {
        runCatching { JarWidgetRenderer.render(state(0.5f), 0, 100) }
            .onSuccess { error("expected IAE") }
        runCatching { JarWidgetRenderer.render(state(0.5f), 100, -1) }
            .onSuccess { error("expected IAE") }
    }
}
