package com.oni3xe.chromekit

import com.oni3xe.chromekit.theme.ChromekitThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShadowBandsTest {
    @Test fun shadowOffDrawsNothingInEveryThemeGlassOrNot() {
        for (th in ChromekitThemes.all) {
            for (glass in listOf(false, true)) {
                assertEquals("${th.id} glass=$glass", 0, panelShadowBands(th.tokens(glass, false), 3f).size)
            }
        }
    }

    @Test fun shadowOnDrawsInEveryTheme() {
        for (th in ChromekitThemes.all) {
            assertTrue(th.id, panelShadowBands(th.tokens(false, true), 3f).isNotEmpty())
            assertTrue(th.id, panelShadowBands(th.tokens(true, true), 3f).isNotEmpty())
        }
    }

    @Test fun zeroAlphaOrRadiusIsEmpty() {
        assertEquals(0, shadowBands(24f, 3f, 0f, 0f).size)
        assertEquals(0, shadowBands(0f, 0f, 0.2f, 0.5f).size)
    }

    @Test fun bandsAreAtMostOnePixelSoThereAreNoVisibleSteps() {
        for (density in listOf(1f, 2f, 2.75f, 3.5f)) {
            val b = shadowBands(8f * density, 1f * density, 0.2f, 0.5f)
            for (i in b.indices step 3) assertTrue(b[i + 1] <= 1f + 1e-4f)
            // Adjacent bands touch: no gaps, no overlap
            for (i in 3 until b.size step 3) assertEquals(b[i - 3] + b[i - 2] / 2f + b[i + 1] / 2f, b[i], 1e-3f)
        }
    }

    @Test fun alphaFadesSmoothlyFromTheEdgeToZero() {
        val b = shadowBands(24f, 3f, 0.2f, 0.5f)
        assertTrue(abs(b[2] - 0.5f) < 0.06f)
        for (i in 5 until b.size step 3) {
            assertTrue("never brighter outward", b[i] <= b[i - 3] + 1e-6f)
            assertTrue("no jump between bands", b[i - 3] - b[i] < 0.11f)
        }
        assertTrue(b[b.size - 1] < 0.02f)
    }

    @Test fun shadowTokensAreNarrowAndSubtle() {
        for (th in ChromekitThemes.all) {
            assertTrue(th.id, th.shadow.radiusDp <= 8f)
            assertTrue(th.id, th.shadow.alpha <= 0.25f)
            assertTrue(th.id, th.shadow.edgeAlpha <= 0.6f)
        }
    }
}
