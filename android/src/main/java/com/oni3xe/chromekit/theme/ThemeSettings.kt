package com.oni3xe.chromekit.theme

import android.content.Context
import android.content.SharedPreferences

/** The few key/value calls theme persistence needs; [SharedPreferences] in apps, a map in tests. */
interface ThemePrefs {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun put(key: String, value: String)
    fun put(key: String, value: Boolean)
}

class SharedPreferencesThemePrefs(private val prefs: SharedPreferences) : ThemePrefs {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    companion object {
        /** The kit's own preferences file, private to the app. */
        const val FILE = "chromekit_theme"
        fun of(context: Context) = SharedPreferencesThemePrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/**
 * Persisted theme choices. [localThemeId] is this app's own theme; [followLauncher] (default on)
 * uses the trusted launcher's shared theme when there is one; [shareToAllApps] (default off) is the
 * launcher's "Apply this theme to all cybr apps" checkbox (shares theme, glass, Shadow and wallpaper);
 * [glass] and [shadow] (both default off) are independent toggles layered on whichever theme is
 * active. Unknown stored ids read as the default.
 */
class ThemeSettings(private val prefs: ThemePrefs) {
    var localThemeId: String
        get() = ChromekitThemes.byId(prefs.getString(KEY_THEME)).id
        set(value) = prefs.put(KEY_THEME, ChromekitThemes.byId(value).id)

    var followLauncher: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW, true)
        set(value) = prefs.put(KEY_FOLLOW, value)

    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, ChromekitThemes.GLASS_DEFAULT)
        set(value) = prefs.put(KEY_GLASS, value)

    var shadow: Boolean
        get() = prefs.getBoolean(KEY_SHADOW, ChromekitThemes.SHADOW_DEFAULT)
        set(value) = prefs.put(KEY_SHADOW, value)

    var shareToAllApps: Boolean
        get() = prefs.getBoolean(KEY_SHARE, false)
        set(value) = prefs.put(KEY_SHARE, value)

    /** The glass blur style; never chosen (or unknown) reads as the default, glass. */
    var blurStyle: com.oni3xe.chromekit.views.BlurStyle
        get() = com.oni3xe.chromekit.views.BlurStyle.parse(prefs.getString(KEY_BLUR))
        set(value) = prefs.put(KEY_BLUR, value.value)

    companion object {
        const val KEY_THEME = "theme_id"
        const val KEY_FOLLOW = "follow_launcher"
        const val KEY_SHARE = "share_to_all_apps"
        const val KEY_GLASS = "glass"
        const val KEY_SHADOW = "shadow"
        const val KEY_BLUR = "blur_style"
    }
}

enum class ThemeSource { Local, Launcher }

/**
 * The active look: theme, the glass and Shadow toggles, and (only when following the launcher) the
 * launcher's validated wallpaper reference.
 */
data class ResolvedTheme(
    val theme: ChromekitThemeInfo,
    val source: ThemeSource,
    val glass: Boolean = false,
    val shadow: Boolean = false,
    val wallpaperUri: String? = null,
    /** The background mode: the launcher's while following it, else [BackgroundMode.DEFAULT]. */
    val background: BackgroundMode = BackgroundMode.DEFAULT,
    val blurStyle: com.oni3xe.chromekit.views.BlurStyle = com.oni3xe.chromekit.views.BlurStyle.DEFAULT,
) {
    val tokens: CybrTokens get() = theme.tokens(glass, shadow, blurStyle)
}

/**
 * Picks the active look. The launcher's [shared] look (theme + glass + Shadow + wallpaper) wins only
 * when this app follows the launcher and it came from a trusted launcher; otherwise the local choice.
 */
fun resolveTheme(
    localThemeId: String?,
    followLauncher: Boolean,
    shared: SharedTheme?,
    localGlass: Boolean = false,
    localShadow: Boolean = false,
    localBlurStyle: com.oni3xe.chromekit.views.BlurStyle = com.oni3xe.chromekit.views.BlurStyle.DEFAULT,
): ResolvedTheme =
    if (followLauncher && shared != null) {
        ResolvedTheme(ChromekitThemes.byId(shared.themeId), ThemeSource.Launcher, shared.glass, shared.shadow,
            shared.wallpaperUri, shared.background, shared.blurStyle)
    } else {
        ResolvedTheme(ChromekitThemes.byId(localThemeId), ThemeSource.Local, localGlass, localShadow, blurStyle = localBlurStyle)
    }
