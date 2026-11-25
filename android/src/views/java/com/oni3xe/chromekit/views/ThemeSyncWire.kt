package com.oni3xe.chromekit.views

/**
 * The launcher theme provider's wire format, in one place for both ends: the launcher's provider
 * builds rows with [row]; chromekit's `LauncherThemeContract` reads them (its constants alias these).
 * Framework-free, so Views apps compile it in too.
 */
object ThemeSyncWire {
    const val LAUNCHER_PACKAGE = "com.oni3xe.cybrlauncher"
    const val AUTHORITY = "$LAUNCHER_PACKAGE.chromekit.theme"
    const val PATH = "theme"
    const val WALLPAPER_PATH = "wallpaper"
    const val COLUMN_THEME = "theme_id"
    const val COLUMN_SHARED = "shared"
    const val COLUMN_GLASS = "glass"
    const val COLUMN_SHADOW = "shadow"
    const val COLUMN_WALLPAPER = "wallpaper"
    const val COLUMN_BACKGROUND = "background"
    const val COLUMN_BLUR_STYLE = "blur_style"
    /** Glitch overlay: on only for exactly 1 with a known type (fail closed). */
    const val COLUMN_GLITCH = "glitch"
    const val COLUMN_GLITCH_TYPE = "glitch_type"
    const val COLUMN_GLITCH_STRENGTH = "glitch_strength"

    /** Wallpaper ids: 1-64 of A-Z a-z 0-9 _ -. */
    val WALLPAPER_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Column order of the one theme row. */
    val COLUMNS: Array<String> = arrayOf(
        COLUMN_THEME, COLUMN_SHARED, COLUMN_GLASS, COLUMN_SHADOW, COLUMN_WALLPAPER, COLUMN_BACKGROUND, COLUMN_BLUR_STYLE,
        COLUMN_GLITCH, COLUMN_GLITCH_TYPE, COLUMN_GLITCH_STRENGTH,
    )

    /** `content://<AUTHORITY>/wallpaper/<id>`, or null for an invalid id. */
    fun wallpaperUri(id: String?): String? = id?.takeIf { WALLPAPER_ID.matches(it) }?.let { "content://$AUTHORITY/$WALLPAPER_PATH/$it" }

    /** The row while sharing is off: `shared` = 0, nothing else. */
    fun unsharedRow(): Array<Any?> = arrayOfNulls<Any?>(COLUMNS.size).also { it[1] = 0 }

    /** The shared row, in [COLUMNS] order. [background] is a chromekit BackgroundMode value. */
    fun row(
        themeId: String, glass: Boolean, shadow: Boolean, wallpaperId: String?, background: String, blurStyle: BlurStyle,
        glitch: GlitchSpec? = null,
    ): Array<Any?> =
        arrayOf(themeId, 1, if (glass) 1 else 0, if (shadow) 1 else 0, wallpaperUri(wallpaperId), background, blurStyle.value,
            if (glitch != null) 1 else 0, glitch?.type?.value, glitch?.strength)
}
