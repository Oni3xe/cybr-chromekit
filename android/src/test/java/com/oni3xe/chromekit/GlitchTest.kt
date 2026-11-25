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

    @Test fun frequencyOneKeepsTheOriginalCadenceExactly() {
        val original = listOf(7_000L..12_000L, 5_000L..9_000L, 3_000L..6_000L)
        for (s in 1..3) {
            assertEquals(original[s - 1], GlitchMath.gapRangeMs(s, 1))
            assertEquals(original[s - 1], GlitchMath.gapRangeMs(s))
            // Same random sequence as the two-argument call
            assertEquals(GlitchMath.gapMs(s, Random(11)), GlitchMath.gapMs(s, 1, Random(11)))
        }
        assertEquals(1, com.oni3xe.chromekit.views.GlitchSpec(GlitchType.DEFAULT, 1).frequency)
    }

    @Test fun cadencePerFrequencyLevel() {
        val rnd = Random(5)
        // level 2: 0.6x the gaps, level 3: 0.35x, never under the floor
        assertEquals(4_200L..7_200L, GlitchMath.gapRangeMs(1, 2))
        assertEquals(2_450L..4_200L, GlitchMath.gapRangeMs(1, 3))
        assertEquals(1_800L..3_600L, GlitchMath.gapRangeMs(3, 2))
        assertEquals(1_500L..2_100L, GlitchMath.gapRangeMs(3, 3))
        for (s in 1..3) {
            assertTrue(GlitchMath.gapRangeMs(s, 3).last < GlitchMath.gapRangeMs(s, 2).last)
            assertTrue(GlitchMath.gapRangeMs(s, 2).last < GlitchMath.gapRangeMs(s, 1).last)
            for (fq in 1..3) {
                val range = GlitchMath.gapRangeMs(s, fq)
                assertTrue(range.first >= GlitchMath.GAP_FLOOR_MS)
                repeat(200) { assertTrue(GlitchMath.gapMs(s, fq, rnd) in range) }
                assertTrue("s=$s f=$fq", GlitchMath.maxDutyCycle(s, fq) <= GlitchMath.dutyCap(fq))
            }
        }
        assertTrue(GlitchMath.dutyCap(3) <= 0.3f)
    }

    @Test fun frequencyIsValidated() {
        for (bad in listOf(0, 4)) {
            assertTrue(runCatching { com.oni3xe.chromekit.views.GlitchSpec(GlitchType.DEFAULT, 1, bad) }.isFailure)
        }
    }

    @Test fun tintedDrawsOnlyTheMainColourAtAHigherAlpha() {
        val main = 0xFFC8383C.toInt(); val accent = 0xFF8DA5A7.toInt()
        for (st in 1..3) {
            val on = GlitchMath.ink(main, accent, st, tinted = true)
            val off = GlitchMath.ink(main, accent, st, tinted = false)
            assertEquals(setOf(main), on.colors)
            assertTrue(on.alpha >= off.alpha * 1.5f && on.alpha <= GlitchMath.TINT_ALPHA_MAX)
            // Neutral unchanged: main + accent marks, black flicker, the original alpha
            assertEquals(com.oni3xe.chromekit.views.GlitchInk(main, accent, 0xFF000000.toInt(), GlitchMath.alpha(st)), off)
        }
        assertEquals(0.252f, GlitchMath.alpha(1, true), 1e-4f)
        assertEquals(0.36f, GlitchMath.alpha(2, true), 1e-4f)
        assertEquals(0.45f, GlitchMath.alpha(3, true), 1e-4f)
        assertFalse(com.oni3xe.chromekit.views.GlitchSpec(GlitchType.DEFAULT, 1).tinted)
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
