package com.oni3xe.chromekit.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {
    @Test fun tenThemesInPickerOrder() {
        assertEquals(
            listOf("red", "purple", "orange", "blue", "pink", "green", "cyan", "amber", "monochrome", "mocha"),
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

    @Test fun everyThemeIsDarkNeverWhiteAndKeepsErrorFixed() {
        val white = Color(0xFFFFFFFFL)
        for (th in ChromekitThemes.all) {
            val c = th.tokens.colors
            listOf(c.bg, c.surface1, c.surface2, c.red, c.redBright, c.cyan, c.cyanBright, c.text).forEach { assertNotEquals(th.id, white, it) }
            assertTrue("${th.id} bg is dark", c.bg.red < 0.1f && c.bg.green < 0.1f && c.bg.blue < 0.1f)
            assertEquals(th.id, ChromekitTokens.colors.error, c.error)
            assertEquals(th.id, ChromekitTokens.colors.warning, c.warning)
            assertEquals(th.id, ChromekitTokens.type, th.tokens.type)
            assertEquals(th.id, ChromekitTokens.shapes, th.tokens.shapes)
        }
    }

    @Test fun onlyMochaIsLowContrast() {
        assertEquals(listOf("mocha"), ChromekitThemes.all.filter { it.lowContrast }.map { it.id })
    }
}
