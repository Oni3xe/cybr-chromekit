package com.oni3xe.chromekit

import com.oni3xe.chromekit.views.GlitchMath
import com.oni3xe.chromekit.views.GlitchType
import com.oni3xe.chromekit.views.ThemeSyncWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GlitchTest {
    @Test fun typesParseExactly() {
        for (v in listOf(null, "", "SCANLINES", "vhs")) assertNull(GlitchType.parse(v))
        for (t in GlitchType.entries) assertEquals(t, GlitchType.parse(t.value))
    }

    @Test fun firstBurstComesWithinTwoSeconds() {
        val rnd = Random(3)
        repeat(500) { assertTrue(GlitchMath.firstMs(rnd) in 1_000L..2_000L) }
    }

    @Test fun timingIsIntermittentAndBounded() {
        val rnd = Random(7)
        for (s in 1..3) {
            repeat(200) {
                assertTrue(GlitchMath.gapMs(s, rnd) in 3_000L..12_000L)
                assertTrue(GlitchMath.burstMs(s, rnd) in 160L..420L)
            }
            // At most 15% of the time drawing (a few frames at 20fps), even at strength 3
            assertTrue(GlitchMath.maxDutyCycle(s) <= 0.15f)
        }
    }

    @Test fun visibleButSubtle() {
        assertTrue(GlitchMath.alpha(1) >= 0.12f) // round 11's 0.06 was invisible on a device
        assertTrue(GlitchMath.alpha(1) < GlitchMath.alpha(2) && GlitchMath.alpha(2) < GlitchMath.alpha(3))
        assertTrue(GlitchMath.alpha(3) <= 0.3f)
        for (t in GlitchType.entries) assertTrue(GlitchMath.marks(t, 1) <= GlitchMath.marks(t, 3))
    }

    @Test fun onlyTheAnimatorScaleMeansReducedMotion() {
        assertTrue(GlitchMath.reducedMotion(0f))
        assertFalse(GlitchMath.reducedMotion(1f))
        assertFalse(GlitchMath.reducedMotion(0.5f))
    }

    @Test fun glitchIsNotSynced() {
        assertTrue(ThemeSyncWire.COLUMNS.none { it.startsWith("glitch") })
    }
}
