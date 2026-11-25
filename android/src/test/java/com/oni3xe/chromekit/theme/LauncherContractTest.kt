package com.oni3xe.chromekit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherContractTest {
    private val base = "content://com.oni3xe.cybrlauncher.chromekit.theme/wallpaper/"

    @Test fun parsesAFullRow() {
        val s = LauncherThemeContract.parse("noir", 1, 1, 0, base + "current_01")!!
        assertEquals("noir", s.themeId)
        assertTrue(s.glass)
        assertFalse(s.shadow)
        assertEquals(base + "current_01", s.wallpaperUri)
    }

    @Test fun notSharedOrUnknownThemeIsNull() {
        assertNull(LauncherThemeContract.parse("noir", 0, 1, 1, null))
        assertNull(LauncherThemeContract.parse("noir", null, 1, 1, null))
        assertNull(LauncherThemeContract.parse("noir", 2, 1, 1, null))
        assertNull(LauncherThemeContract.parse(null, 1, 1, 1, null))
        assertNull(LauncherThemeContract.parse("NOIR", 1, 1, 1, null))
        assertNull(LauncherThemeContract.parse("chopper", 1, 1, 1, null))
    }

    @Test fun missingOrMalformedTogglesAreOff() {
        val s = LauncherThemeContract.parse("red", 1, null, 7, null)!!
        assertFalse(s.glass)
        assertFalse(s.shadow)
        assertFalse(LauncherThemeContract.parse("red", 1, -1, 1, null)!!.glass)
        assertNull(LauncherThemeContract.parse("red", 1, 0, 0, null)!!.wallpaperUri)
    }

    @Test fun wallpaperMustBeTheLaunchersOwnProviderPath() {
        listOf(
            "", "w1", "file:///sdcard/w.png", "https://example.com/w.png",
            "content://com.evil.provider/wallpaper/w1",
            "content://com.oni3xe.cybrlauncher.chromekit.theme.evil/wallpaper/w1",
            "content://user@com.oni3xe.cybrlauncher.chromekit.theme/wallpaper/w1",
            "content://com.oni3xe.cybrlauncher.chromekit.theme:80/wallpaper/w1",
            base, base + "../theme", base + "a/b", base + "w1?x=1", base + "w1#f", base + "w%2F1",
            base + "w 1", base + "a".repeat(65), "content://com.oni3xe.cybrlauncher.chromekit.theme/theme",
            base.uppercase() + "W1",
        ).forEach { bad ->
            assertNull(bad, LauncherThemeContract.validWallpaper(bad))
            assertNull(bad, LauncherThemeContract.parse("red", 1, 1, 1, bad)!!.wallpaperUri)
        }
        assertNull(LauncherThemeContract.validWallpaper(null))
        assertEquals(base + "a".repeat(64), LauncherThemeContract.validWallpaper(base + "a".repeat(64)))
        assertEquals(base + "Wall-paper_2", LauncherThemeContract.validWallpaper(base + "Wall-paper_2"))
    }

    @Test fun backgroundModeParsesExactValues() {
        assertEquals(BackgroundMode.NONE, LauncherThemeContract.parse("red", 1, 0, 0, null, "none")!!.background)
        assertEquals(BackgroundMode.THEME, LauncherThemeContract.parse("red", 1, 0, 0, null, "theme")!!.background)
        assertEquals(BackgroundMode.DEFAULT, LauncherThemeContract.parse("red", 1, 0, 0, null, "default")!!.background)
        val base = "content://${LauncherThemeContract.AUTHORITY}/wallpaper/"
        val s = LauncherThemeContract.parse("red", 1, 0, 0, base + "custom_1", "import")!!
        assertEquals(BackgroundMode.IMPORT, s.background)
        assertEquals(base + "custom_1", s.wallpaperUri)
    }

    @Test fun unknownOrMissingBackgroundFallsBackToDefault() {
        for (v in listOf(null, "", "NONE", "Theme", "black", "import ", "none;drop", "1")) {
            assertEquals(v.toString(), BackgroundMode.DEFAULT, LauncherThemeContract.parse("red", 1, 0, 0, null, v)!!.background)
        }
        // Old launchers send no background column at all
        assertEquals(BackgroundMode.DEFAULT, LauncherThemeContract.parse("red", 1, 0, 0, null)!!.background)
    }

    @Test fun backgroundNeverMakesAnUnsharedRowValid() {
        assertNull(LauncherThemeContract.parse("red", 0, 0, 0, null, "theme"))
        assertNull(LauncherThemeContract.parse("bogus", 1, 0, 0, null, "theme"))
    }

    @Test fun defaultWithASharedWallpaperRendersIt() {
        val uri = "content://${LauncherThemeContract.AUTHORITY}/wallpaper/katana_tower"
        val s = LauncherThemeContract.parse("red", 1, 1, 0, uri, "default")!!
        assertEquals(BackgroundMode.IMPORT, s.background)
        assertEquals(uri, s.wallpaperUri)
        // No or an invalid URI: DEFAULT stays DEFAULT (and the bad URI is dropped)
        assertEquals(BackgroundMode.DEFAULT, LauncherThemeContract.parse("red", 1, 1, 0, null, "default")!!.background)
        val bad = LauncherThemeContract.parse("red", 1, 1, 0, "content://evil.example/wallpaper/katana_tower", "default")!!
        assertEquals(BackgroundMode.DEFAULT, bad.background)
        assertEquals(null, bad.wallpaperUri)
        // Other modes are untouched
        for (m in listOf("none", "theme", "import")) {
            assertEquals(BackgroundMode.parse(m), LauncherThemeContract.parse("red", 1, 1, 0, uri, m)!!.background)
        }
    }
}
