package com.oni3xe.chromekit.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {
    @Test fun elevenThemesInPickerOrder() {
        assertEquals(
            listOf("red", "purple", "orange", "blue", "pink", "green", "cyan", "amber", "monochrome", "mocha", "noir"),
            ChromekitThemes.all.map { it.id },
        )
    }

    @Test fun redIsDefaultAndIdenticalToTodaysTokens() {
        assertEquals("red", ChromekitThemes.DEFAULT_ID)
        assertSame(ChromekitTokens, ChromekitThemes.default.tokens)
        assertEquals(ChromekitTokens.colors.red, ChromekitTokens.colors.warning)
    }

    @Test fun unknownIdsFallBackToDefault() {
        assertEquals("red", ChromekitThemes.byId(null).id)
        assertEquals("red", ChromekitThemes.byId("nope").id)
        assertEquals("mocha", ChromekitThemes.byId("mocha").id)
    }

    @Test fun everyThemeIsDarkNeverWhiteAndKeepsAlertsFixed() {
        val white = Color(0xFFFFFFFFL)
        for (th in ChromekitThemes.all) {
            val c = th.tokens.colors
            listOf(c.bg, c.surface1, c.surface2, c.red, c.redBright, c.cyan, c.cyanBright, c.text).forEach { assertNotEquals(th.id, white, it) }
            assertTrue("${th.id} bg is dark", c.bg.red < 0.1f && c.bg.green < 0.1f && c.bg.blue < 0.1f)
            assertEquals(th.id, ChromekitTokens.colors.warning, c.warning)
            assertEquals(th.id, ChromekitTokens.colors.green, c.green)
            assertEquals(th.id, ChromekitTokens.colors.gold, c.gold)
            assertEquals(th.id, ChromekitTokens.type, th.tokens.type)
            assertEquals(th.id, ChromekitTokens.shapes, th.tokens.shapes)
        }
    }

    @Test fun errorIsThemedKatanaKeepsTheMagentaAndOnlyErrorMayBeWhite() {
        assertEquals(Color(0xFFFF2BD6L), ChromekitThemes.byId("red").tokens.colors.error)
        val errors = ChromekitThemes.all.map { it.tokens.colors.error }
        assertEquals(errors.size, errors.toSet().size)
        assertEquals(Color(0xFFFFFFFFL), ChromekitThemes.byId("monochrome").tokens.colors.error)
        for (th in ChromekitThemes.all) assertNotEquals(th.id, th.tokens.colors.red, th.tokens.colors.error)
    }

    @Test fun codeNamesWithStableIds() {
        assertEquals(
            listOf("KATANA", "PHANTOM", "EMBER", "ICEBREAKER", "SAKURA", "MATRIX", "NEON", "SODIUM", "GHOST", "NOMAD", "CHOPPER"),
            ChromekitThemes.all.map { it.label },
        )
        assertEquals("KATANA · Red", ChromekitThemes.default.displayName)
        assertEquals("CHOPPER · Noir", ChromekitThemes.byId("noir").displayName)
    }

    @Test fun shadowIsOffByDefaultAndIndependentOfGlass() {
        assertEquals(false, ChromekitThemes.SHADOW_DEFAULT)
        for (th in ChromekitThemes.all) {
            assertEquals(null, th.tokens(false, false).shadow)
            assertSame(th.tokens, th.tokens(false, false))
            val s = th.tokens(false, true)
            assertEquals(th.shadow, s.shadow)
            assertEquals(null, s.glass)
            assertEquals(th.tokens.colors, s.colors)
            val both = th.tokens(true, true)
            assertEquals(th.glass, both.glass)
            assertEquals(th.shadow, both.shadow)
            assertTrue(th.shadow.alpha in 0.01f..1f && th.shadow.radiusDp > 0f)
            // Brightest at the border: the edge band is brighter than the outer fade and inside its radius.
            assertTrue(th.shadow.edgeAlpha in th.shadow.alpha..1f)
            assertTrue(th.shadow.edgeWidthDp > 0f && th.shadow.edgeWidthDp < th.shadow.radiusDp)
            assertTrue(th.shadow.chamferDp > 0f)
        }
    }

    @Test fun glassIsOffByDefaultAndLayersOnAnyTheme() {
        assertEquals(false, ChromekitThemes.GLASS_DEFAULT)
        for (th in ChromekitThemes.all) {
            assertSame(th.tokens, th.tokens(false))
            assertEquals(null, th.tokens.glass)
            val g = th.tokens(true)
            assertEquals(th.glass, g.glass)
            assertEquals(th.glass.surfaceAlpha, g.colors.surface1.alpha, 0.01f)
            assertEquals(th.glass.surfaceAlpha, g.colors.scrim.alpha, 0.01f)
            assertEquals(th.tokens.colors.red, g.colors.red)
            assertEquals(th.tokens.shapes, g.shapes)
            assertTrue(th.glass.blurRadiusDp > 0f && th.glass.borderAlpha in 0f..1f && th.glass.glowAlpha in 0f..1f)
        }
    }

    @Test fun onlyMochaIsLowContrast() {
        assertEquals(listOf("mocha"), ChromekitThemes.all.filter { it.lowContrast }.map { it.id })
    }
}
