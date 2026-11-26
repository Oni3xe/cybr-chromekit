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

    @Test fun redIsDefaultAndTodaysTokensExceptTheTintedBg() {
        assertEquals("red", ChromekitThemes.DEFAULT_ID)
        val d = ChromekitThemes.default.tokens
        assertEquals(ChromekitTokens.copy(colors = ChromekitTokens.colors.copy(bg = d.colors.bg)), d)
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
            assertTrue("${th.id} bg is dark", c.bg.red < 0.11f && c.bg.green < 0.11f && c.bg.blue < 0.11f) // CHOPPER slate #0E141A
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
            assertTrue(th.shadow.edgeAlpha in 0f..1f)
            assertTrue(th.shadow.edgeWidthDp >= 0f && th.shadow.edgeWidthDp < th.shadow.radiusDp)
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

    @Test fun everyThemeUsesTheDoubleOppositePanelCut() {
        for (th in ChromekitThemes.all) {
            for (tokens in listOf(th.tokens(false, false), th.tokens(true, false), th.tokens(false, true), th.tokens(true, true))) {
                assertEquals(th.id, 10f, tokens.shapes.panelCut.value, 0f)
                assertTrue(th.id, tokens.shapes.panel is com.oni3xe.chromekit.OpposingChamferShape)
            }
        }
    }

    @Test fun theSixDpChamferIsTheDoubleCutToo() {
        for (th in ChromekitThemes.all) {
            for (tokens in listOf(th.tokens(false, false), th.tokens(true, true))) {
                val shape = tokens.shapes.chamfer
                assertTrue(th.id, shape is com.oni3xe.chromekit.ChamferShape)
                assertEquals(th.id, com.oni3xe.chromekit.Tokens.Shape.CHAMFER_DP, (shape as com.oni3xe.chromekit.ChamferShape).cut.value, 0f)
            }
        }
    }

    @Test fun onlyMochaIsLowContrast() {
        assertEquals(listOf("mocha"), ChromekitThemes.all.filter { it.lowContrast }.map { it.id })
    }

    @Test fun backgroundIsAVeryDarkTintOfTheMainColour() {
        for (th in ChromekitThemes.all.filter { it.id != "noir" }) {
            val c = th.tokens.colors
            for ((bg, red) in listOf(c.bg.red to c.red.red, c.bg.green to c.red.green, c.bg.blue to c.red.blue)) {
                assertEquals(th.id, red * 0.03f, bg, 1.5f / 255f)
            }
            // Surfaces stay readable above it
            val sum = { col: androidx.compose.ui.graphics.Color -> col.red + col.green + col.blue }
            assertTrue(th.id, sum(c.surface1) > sum(c.bg) * 1.5f)
            assertTrue(th.id, sum(c.surface2) > sum(c.surface1))
            assertTrue(th.id, sum(c.bg) > 0f)
        }
    }

    @Test fun chopperIsInvertedSlateBackgroundDarkerPanels() {
        val c = ChromekitThemes.byId("noir").tokens.colors
        // bg = CHOPPER's former panel colour; panels and list items darker than it
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0E141A), c.bg)
        val sum = { col: androidx.compose.ui.graphics.Color -> col.red + col.green + col.blue }
        assertTrue(sum(c.surface1) * 1.5f < sum(c.bg))
        assertTrue(sum(c.surface2) < sum(c.bg))
        assertTrue(sum(c.surface1) > 0f)
    }
}
