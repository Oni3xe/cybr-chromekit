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
        assertFalse(s.glass)
        assertFalse(s.shadow)
    }

    @Test fun persistsAcrossInstances() {
        val prefs = MapPrefs()
        ThemeSettings(prefs).apply { localThemeId = "cyan"; followLauncher = false; shareToAllApps = true; glass = true; shadow = true }
        val s = ThemeSettings(prefs)
        assertEquals("cyan", s.localThemeId)
        assertFalse(s.followLauncher)
        assertTrue(s.shareToAllApps)
        assertTrue(s.glass)
        assertTrue(s.shadow)
    }

    @Test fun unknownStoredIdReadsAsDefault() {
        val prefs = MapPrefs().apply { m[ThemeSettings.KEY_THEME] = "removed-theme" }
        assertEquals("red", ThemeSettings(prefs).localThemeId)
        ThemeSettings(prefs).localThemeId = "bogus"
        assertEquals("red", prefs.m[ThemeSettings.KEY_THEME])
    }

    @Test fun resolveUsesTheLaunchersWholeLookOnlyWhenFollowing() {
        val shared = SharedTheme("blue", glass = true, shadow = true, wallpaperUri = "content://com.oni3xe.cybrlauncher.chromekit.theme/wallpaper/w1")
        val r = resolveTheme("green", true, shared, localGlass = false, localShadow = false)
        assertEquals(ThemeSource.Launcher, r.source)
        assertEquals("blue", r.theme.id)
        assertTrue(r.glass && r.shadow)
        assertEquals(shared.wallpaperUri, r.wallpaperUri)
        assertEquals(r.theme.glass, r.tokens.glass)

        val local = resolveTheme("green", false, shared, localGlass = false, localShadow = true)
        assertEquals(ThemeSource.Local, local.source)
        assertEquals("green", local.theme.id)
        assertFalse(local.glass)
        assertTrue(local.shadow)
        assertEquals(null, local.wallpaperUri)

        assertEquals("green", resolveTheme("green", true, null).theme.id)
        assertEquals("red", resolveTheme(null, true, null).theme.id)
    }
}
