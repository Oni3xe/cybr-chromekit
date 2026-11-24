package com.oni3xe.chromekit.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.Executors

/**
 * Runtime theme state for one app: the persisted local choice, whether it follows the launcher, and
 * the launcher's shared theme (read off the main thread, refreshed on start and on provider change).
 * Changing anything recomposes [ChromekitTheme] at once; no restart.
 */
@Stable
class ChromekitThemeState internal constructor(
    private val settings: ThemeSettings,
    private val client: LauncherThemeClient?,
    /** Launcher only: tells readers its provider changed (ContentResolver.notifyChange). */
    private val notifyShared: () -> Unit = {},
) {
    var localThemeId by mutableStateOf(settings.localThemeId); private set
    var followLauncher by mutableStateOf(settings.followLauncher); private set
    var shareToAllApps by mutableStateOf(settings.shareToAllApps); private set
    var glass by mutableStateOf(settings.glass); private set

    /** The trusted launcher's shared theme id, or null. */
    var sharedThemeId by mutableStateOf<String?>(null); private set

    /** True in the launcher itself: it shares its theme instead of following one. */
    val isLauncher: Boolean get() = client == null

    val resolved: ResolvedTheme get() = resolveTheme(localThemeId, followLauncher && !isLauncher, sharedThemeId)
    val active: ChromekitThemeInfo get() = resolved.theme

    /** True when the launcher's shared theme is in use ("Following launcher"). */
    val followingLauncher: Boolean get() = resolved.source == ThemeSource.Launcher

    /** Picks a local theme. While following the launcher this also breaks away, so the pick shows at once. */
    fun selectTheme(id: String) {
        settings.localThemeId = id
        localThemeId = settings.localThemeId
        if (followingLauncher) useLauncherTheme(false)
        if (isLauncher) notifyShared()
    }

    fun useLauncherTheme(follow: Boolean) {
        settings.followLauncher = follow
        followLauncher = follow
        if (follow) refresh()
    }

    /** Turns the glass variant on or off for this app (any theme). */
    fun useGlass(on: Boolean) {
        settings.glass = on
        glass = on
    }

    /** The active token set, glass applied when on. */
    val tokens: CybrTokens get() = active.tokens(glass)

    /** Launcher only: the "Apply this theme to all cybr apps" checkbox, read by its provider. */
    fun shareWithAllApps(share: Boolean) {
        settings.shareToAllApps = share
        shareToAllApps = share
        notifyShared()
    }

    private val main = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var started = false

    /** Re-reads the launcher's shared theme in the background. */
    fun refresh() {
        val c = client ?: return
        io.execute {
            val id = c.readSharedThemeId()
            main.post { sharedThemeId = id }
        }
    }

    internal fun start(context: Context) {
        if (started || client == null) return
        started = true
        runCatching { context.contentResolver.registerContentObserver(LauncherThemeContract.URI, false, observer) }
        refresh()
    }

    internal fun stop(context: Context) {
        if (!started) return
        started = false
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    companion object {
        private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "chromekit-theme").apply { isDaemon = true } }

        /**
         * One state per process. Apps call this (or [rememberChromekitThemeState]); the launcher itself
         * never reads a provider.
         */
        @Volatile private var instance: ChromekitThemeState? = null

        fun get(context: Context): ChromekitThemeState = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val isLauncher = app.packageName == LauncherThemeContract.LAUNCHER_PACKAGE
                ChromekitThemeState(
                    ThemeSettings(SharedPreferencesThemePrefs.of(app)),
                    if (isLauncher) null else LauncherThemeClient(app),
                ) { runCatching { app.contentResolver.notifyChange(LauncherThemeContract.URI, null) } }.also { instance = it }
            }
        }
    }
}

@Composable
fun rememberChromekitThemeState(): ChromekitThemeState {
    val context = LocalContext.current
    return remember { ChromekitThemeState.get(context) }
}

/**
 * [CybrTheme] driven by the runtime theme: wrap an app's content in this instead of CybrTheme to get
 * theme switching and launcher sync. Listens for launcher changes while in composition.
 */
@Composable
fun ChromekitTheme(state: ChromekitThemeState = rememberChromekitThemeState(), content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    DisposableEffect(state) {
        state.start(context)
        onDispose { state.stop(context) }
    }
    CybrTheme(tokens = state.tokens, content = content)
}
