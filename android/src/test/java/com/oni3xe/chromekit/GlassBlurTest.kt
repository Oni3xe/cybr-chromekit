package com.oni3xe.chromekit

import com.oni3xe.chromekit.theme.ChromekitThemes
import com.oni3xe.chromekit.views.BlurStyle
import com.oni3xe.chromekit.views.GlassBackdropTint
import com.oni3xe.chromekit.views.GlassBlurMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBlurTest {
    @Test fun defaultIsGlass() {
        assertEquals(BlurStyle.GLASS, BlurStyle.DEFAULT)
        assertEquals(BlurStyle.GLASS, ChromekitThemes.BLUR_STYLE_DEFAULT)
        assertEquals(BlurStyle.GLASS, BlurStyle.parse(null))
        assertEquals(BlurStyle.GLASS, BlurStyle.parse("Plain"))
        assertEquals(BlurStyle.PLAIN, BlurStyle.parse("plain"))
    }

    @Test fun identityMatrixAtNeutral() {
        val m = GlassBlurMath.colorMatrix(1f, 1f)
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        for (i in id.indices) assertEquals("i=$i", id[i], m[i], 1e-6f)
    }

    @Test fun matrixKeepsGreyGreyAndBoostsChroma() {
        val s = ChromekitThemes.GLASS_BLUR
        val m = GlassBlurMath.colorMatrix(s.saturation, s.tone)
        fun apply(r: Float, g: Float, b: Float) = FloatArray(3) { row -> m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b }
        val grey = apply(0.5f, 0.5f, 0.5f)
        for (c in grey) assertEquals(0.5f * s.tone, c, 1e-5f)
        val red = apply(0.6f, 0.2f, 0.2f)
        assertTrue(red[0] - red[1] > 0.4f * s.tone)
        assertEquals(1f, m[18], 0f) // alpha untouched
    }

    @Test fun grainIsDeterministicBalancedAndBounded() {
        var sum = 0.0
        for (y in 0 until 64) for (x in 0 until 64) {
            val n = GlassBlurMath.grain(x, y)
            assertTrue(n in -1f..1f)
            assertEquals(n, GlassBlurMath.grain(x, y), 0f)
            sum += n
        }
        assertTrue(kotlin.math.abs(sum / 4096) < 0.1)
    }

    @Test fun glassTokensAreSubtle() {
        val s = ChromekitThemes.GLASS_BLUR
        assertTrue(s.saturation in 1f..1.4f && s.tone in 1f..1.15f && s.grainAlpha in 0.01f..0.06f && s.edgeAlpha in 0.05f..0.3f)
    }

    @Test fun backdropTintIsAReadableStepUpFromNoTint() {
        // Previous chromekitGlassBackground had no theme-bg tint (alpha 0).
        assertEquals(0.65f, GlassBackdropTint.ALPHA_BLURRED, 0f)
        assertEquals(0.70f, GlassBackdropTint.ALPHA_FALLBACK, 0f)
        assertEquals(0.65f, GlassBackdropTint.alpha(true), 0f)
        assertEquals(0.70f, GlassBackdropTint.alpha(false), 0f)
        assertTrue(GlassBackdropTint.ALPHA_BLURRED in 0.60f..0.70f)
        assertTrue(GlassBackdropTint.ALPHA_FALLBACK in 0.60f..0.75f)
        assertTrue(GlassBackdropTint.ALPHA_FALLBACK >= GlassBackdropTint.ALPHA_BLURRED)
    }
}
