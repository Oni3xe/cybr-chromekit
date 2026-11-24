package com.oni3xe.chromekit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    private class MapPrefs : ThemePrefs {
        val m = HashMap<String, Any>()
        override fun getString(key: String) = m[key] as String?
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun put(key: String, value: String) { m[key] = value }
        override fun put(key: String, value: Boolean) { m[key] = value }
    }

    @Test fun defaults() {
        val s = ThemeSettings(MapPrefs())
        assertEquals("red", s.localThemeId)
        assertTrue(s.followLauncher)
        assertFalse(s.shareToAllApps)
    }

    @Test fun persistsAcrossInstances() {
        val prefs = MapPrefs()
        ThemeSettings(prefs).apply { localThemeId = "cyan"; followLauncher = false; shareToAllApps = true }
        val s = ThemeSettings(prefs)
        assertEquals("cyan", s.localThemeId)
        assertFalse(s.followLauncher)
        assertTrue(s.shareToAllApps)
    }

    @Test fun unknownStoredIdReadsAsDefault() {
        val prefs = MapPrefs().apply { m[ThemeSettings.KEY_THEME] = "removed-theme" }
        assertEquals("red", ThemeSettings(prefs).localThemeId)
        ThemeSettings(prefs).localThemeId = "bogus"
        assertEquals("red", prefs.m[ThemeSettings.KEY_THEME])
    }

    @Test fun resolveUsesSharedOnlyWhenFollowingAndKnown() {
        assertEquals(ResolvedTheme(ChromekitThemes.byId("blue"), ThemeSource.Launcher), resolveTheme("green", true, "blue"))
        assertEquals(ThemeSource.Local, resolveTheme("green", false, "blue").source)
        assertEquals("green", resolveTheme("green", false, "blue").theme.id)
        assertEquals("green", resolveTheme("green", true, null).theme.id)
        assertEquals("green", resolveTheme("green", true, "unknown").theme.id)
        assertEquals("red", resolveTheme(null, true, null).theme.id)
    }
}
