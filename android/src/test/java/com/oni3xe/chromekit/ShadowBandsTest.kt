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
        // No edge band: the fade starts at alpha right at the edge (10dp at 3x)
        val b = shadowBands(30f, 0f, 0.45f, 0f)
        assertTrue("the start of the fade reads bright", b[2] > 0.4f)
        for (i in 5 until b.size step 3) {
            assertTrue("never brighter outward", b[i] <= b[i - 3] + 1e-6f)
            assertTrue("no jump between bands", b[i - 3] - b[i] < 0.04f)
        }
        assertTrue(b[b.size - 1] < 0.02f)
    }

    @Test fun anEdgeBandStillEasesIntoTheFade() {
        val b = shadowBands(24f, 3f, 0.3f, 0.9f)
        assertTrue(b[2] > 0.75f)
        for (i in 5 until b.size step 3) assertTrue(b[i] <= b[i - 3] + 1e-6f)
    }

    @Test fun shadowTokensAreASmoothFadeWithNoEdgeBand() {
        for (th in ChromekitThemes.all) {
            assertEquals(th.id, 0f, th.shadow.edgeWidthDp, 0f)
            assertTrue(th.id, th.shadow.radiusDp in 8f..12f)
            assertTrue(th.id, th.shadow.alpha in 0.3f..0.6f)
        }
    }

    @Test fun screenFrameGlowsInwardOnlyWithShadowOn() {
        for (th in ChromekitThemes.all) {
            // Shadow off: nothing
            assertEquals(th.id, 0, screenFrameShadowBands(th.tokens.copy(shadow = null), 3f, 3f).size)
            val on = th.tokens.copy(shadow = th.shadow)
            val inward = screenFrameShadowBands(on, 3f, 3f)
            val outward = panelShadowBands(on, 3f)
            assertEquals(th.id, outward.size, inward.size)
            assertTrue(th.id, inward.isNotEmpty())
            for (i in inward.indices step 3) {
                // Same fade, mirrored inside the 3px frame line
                assertEquals(-(3f + outward[i]), inward[i], 1e-4f)
                assertTrue(inward[i] + inward[i + 1] / 2f <= -3f + 1e-4f)
                assertEquals(outward[i + 1], inward[i + 1], 0f)
                assertEquals(outward[i + 2], inward[i + 2], 0f)
            }
        }
    }
}
