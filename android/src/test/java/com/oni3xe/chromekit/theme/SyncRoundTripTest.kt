package com.oni3xe.chromekit.theme

import com.oni3xe.chromekit.views.BlurStyle
import com.oni3xe.chromekit.views.ThemeSyncWire
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The launcher builds rows with ThemeSyncWire.row; the kit parses them back unchanged. */
class SyncRoundTripTest {
    private fun parse(row: Array<Any?>): SharedTheme? {
        val m = ThemeSyncWire.COLUMNS.zip(row.toList()).toMap()
        fun l(c: String) = (m[c] as? Int)?.toLong()
        return LauncherThemeContract.parse(m[LauncherThemeContract.COLUMN_THEME] as String?, l(LauncherThemeContract.COLUMN_SHARED),
            l(LauncherThemeContract.COLUMN_GLASS), l(LauncherThemeContract.COLUMN_SHADOW), m[LauncherThemeContract.COLUMN_WALLPAPER] as String?,
            m[LauncherThemeContract.COLUMN_BACKGROUND] as String?, m[LauncherThemeContract.COLUMN_BLUR_STYLE] as String?)
    }

    @Test fun contractAliasesTheWire() {
        assertEquals("com.oni3xe.cybrlauncher.chromekit.theme", LauncherThemeContract.AUTHORITY)
        assertArrayEquals(arrayOf("theme_id", "shared", "glass", "shadow", "wallpaper", "background", "blur_style"), ThemeSyncWire.COLUMNS)
    }

    @Test fun everyFieldRoundTrips() {
        for (theme in ChromekitThemes.all.filter { ChromekitThemes.find(it.id) != null }) for (glass in listOf(true, false)) for (shadow in listOf(true, false))
            for (bg in BackgroundMode.entries) for (blur in BlurStyle.entries) for (wp in listOf(null, "custom_1", "red_glass")) {
                val s = parse(ThemeSyncWire.row(theme.id, glass, shadow, wp, bg.value, blur))!!
                assertEquals(SharedTheme(theme.id, glass, shadow, ThemeSyncWire.wallpaperUri(wp), bg, blur), s)
            }
    }

    @Test fun unsharedRowIsNull() {
        assertNull(parse(ThemeSyncWire.unsharedRow()))
    }

    @Test fun badWallpaperIdIsNotSent() {
        assertNull(ThemeSyncWire.wallpaperUri("../x"))
        assertNull(ThemeSyncWire.wallpaperUri(""))
    }

    @Test fun blurStyleUnknownOrMissingIsGlass() {
        for (v in listOf(null, "", "GLASS", "frosted", " plain")) {
            assertEquals(v.toString(), BlurStyle.GLASS, LauncherThemeContract.parse("red", 1, 1, 0, null, null, v)!!.blurStyle)
        }
        assertEquals(BlurStyle.PLAIN, LauncherThemeContract.parse("red", 1, 1, 0, null, null, "plain")!!.blurStyle)
    }

    @Test fun resolvedThemeCarriesBackgroundAndBlurStyle() {
        val shared = SharedTheme("red", true, false, null, BackgroundMode.THEME, BlurStyle.PLAIN)
        val r = resolveTheme("red", true, shared, false, false, BlurStyle.GLASS)
        assertEquals(BackgroundMode.THEME, r.background)
        assertEquals(BlurStyle.PLAIN, r.blurStyle)
        assertNull(r.tokens.glassBlur) // plain: no glass look
        val local = resolveTheme("red", false, shared, true, false, BlurStyle.GLASS)
        assertEquals(ChromekitThemes.GLASS_BLUR, local.tokens.glassBlur)
        assertNull(resolveTheme("red", false, null, false, false, BlurStyle.GLASS).tokens.glassBlur) // glass off
    }
}
