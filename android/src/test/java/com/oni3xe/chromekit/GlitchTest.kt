package com.oni3xe.chromekit

import com.oni3xe.chromekit.theme.LauncherThemeContract
import com.oni3xe.chromekit.views.BlurStyle
import com.oni3xe.chromekit.views.GlitchMath
import com.oni3xe.chromekit.views.GlitchSpec
import com.oni3xe.chromekit.views.GlitchType
import com.oni3xe.chromekit.views.ThemeSyncWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GlitchTest {
    @Test fun unknownTypeIsOff() {
        for (v in listOf(null, "", "SCANLINES", "vhs")) assertNull(GlitchType.parse(v))
        for (t in GlitchType.entries) assertEquals(t, GlitchType.parse(t.value))
    }

    @Test fun syncedSpecFailsClosed() {
        assertNull(GlitchSpec.parse(null, "scanlines", 2))
        assertNull(GlitchSpec.parse(0, "scanlines", 2))
        assertNull(GlitchSpec.parse(2, "scanlines", 2))
        assertNull(GlitchSpec.parse(1, "vhs", 2))
        assertNull(GlitchSpec.parse(1, null, 2))
        assertEquals(GlitchSpec(GlitchType.TEAR, 3), GlitchSpec.parse(1, "tear", 9))
        assertEquals(GlitchSpec(GlitchType.BLOCKS, 1), GlitchSpec.parse(1, "blocks", -4))
        assertEquals(GlitchSpec(GlitchType.RGB_SPLIT, 1), GlitchSpec.parse(1, "rgb", null))
    }

    @Test fun timingIsIntermittentAndBounded() {
        val rnd = Random(7)
        for (s in 1..3) {
            repeat(200) {
                val gap = GlitchMath.gapMs(s, rnd)
                val burst = GlitchMath.burstMs(s, rnd)
                assertTrue("gap $gap", gap >= 6_000)
                assertTrue("burst $burst", burst in 90..300)
            }
            // At most 5% of the time drawing, at the strongest setting
            assertTrue(GlitchMath.maxDutyCycle(s) <= 0.05f)
        }
    }

    @Test fun strengthRaisesIntensityButStaysSubtle() {
        assertTrue(GlitchMath.alpha(1) < GlitchMath.alpha(2) && GlitchMath.alpha(2) < GlitchMath.alpha(3))
        assertTrue(GlitchMath.alpha(3) <= 0.2f)
        for (t in GlitchType.entries) assertTrue(GlitchMath.marks(t, 1) <= GlitchMath.marks(t, 3))
        assertEquals(GlitchMath.alpha(3), GlitchMath.alpha(99), 0f)
    }

    @Test fun glitchRoundTripsThroughTheWire() {
        for (spec in listOf(null) + GlitchType.entries.flatMap { t -> (1..3).map { GlitchSpec(t, it) } }) {
            val row = ThemeSyncWire.row("red", true, false, null, "default", BlurStyle.GLASS, spec)
            val m = ThemeSyncWire.COLUMNS.zip(row.toList()).toMap()
            fun l(c: String) = (m[c] as? Int)?.toLong()
            val s = LauncherThemeContract.parse("red", l("shared"), l("glass"), l("shadow"), null, "default", "glass",
                l("glitch"), m["glitch_type"] as String?, l("glitch_strength"))!!
            assertEquals(spec, s.glitch)
        }
    }
}
