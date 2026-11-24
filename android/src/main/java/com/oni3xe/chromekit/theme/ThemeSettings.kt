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
 * launcher's "Apply this theme to all cybr apps" checkbox. Unknown stored ids read as the default.
 */
class ThemeSettings(private val prefs: ThemePrefs) {
    var localThemeId: String
        get() = ChromekitThemes.byId(prefs.getString(KEY_THEME)).id
        set(value) = prefs.put(KEY_THEME, ChromekitThemes.byId(value).id)

    var followLauncher: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW, true)
        set(value) = prefs.put(KEY_FOLLOW, value)

    var shareToAllApps: Boolean
        get() = prefs.getBoolean(KEY_SHARE, false)
        set(value) = prefs.put(KEY_SHARE, value)

    companion object {
        const val KEY_THEME = "theme_id"
        const val KEY_FOLLOW = "follow_launcher"
        const val KEY_SHARE = "share_to_all_apps"
    }
}

enum class ThemeSource { Local, Launcher }

data class ResolvedTheme(val theme: ChromekitThemeInfo, val source: ThemeSource)

/**
 * Picks the active theme. The launcher's shared theme wins only when this app follows the launcher
 * and [sharedThemeId] came from a trusted launcher and names a known theme; otherwise the local one.
 */
fun resolveTheme(localThemeId: String?, followLauncher: Boolean, sharedThemeId: String?): ResolvedTheme {
    val shared = if (followLauncher) ChromekitThemes.find(sharedThemeId) else null
    return if (shared != null) ResolvedTheme(shared, ThemeSource.Launcher)
    else ResolvedTheme(ChromekitThemes.byId(localThemeId), ThemeSource.Local)
}
