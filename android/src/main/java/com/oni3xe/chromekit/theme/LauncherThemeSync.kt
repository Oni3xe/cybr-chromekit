package com.oni3xe.chromekit.theme

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import java.io.InputStream
import java.security.MessageDigest

/**
 * The launcher's exported, read-only theme provider. Query [URI] for one row:
 * - [COLUMN_THEME] (String): theme id;
 * - [COLUMN_SHARED] (Int 1/0): "Apply this theme to all cybr apps" is on;
 * - [COLUMN_GLASS], [COLUMN_SHADOW] (Int 1/0): the launcher's glass and Shadow toggles;
 * - [COLUMN_WALLPAPER] (String or null): `content://<AUTHORITY>/wallpaper/<id>`, served by the same
 *   provider's read-only openFile;
 * - [COLUMN_BACKGROUND] (String): the background mode, one of [BackgroundMode.value] (`none`,
 *   `theme`, `default`, `import`). Missing or unknown = [BackgroundMode.DEFAULT].
 * No insert, update, delete or write-mode openFile. No permission: the client pins the launcher's
 * package and signing cert instead.
 */
object LauncherThemeContract {
    const val LAUNCHER_PACKAGE = "com.oni3xe.cybrlauncher"
    const val AUTHORITY = "$LAUNCHER_PACKAGE.chromekit.theme"
    const val PATH = "theme"
    const val WALLPAPER_PATH = "wallpaper"
    val URI: Uri get() = Uri.parse("content://$AUTHORITY/$PATH")
    const val COLUMN_THEME = "theme_id"
    const val COLUMN_SHARED = "shared"
    const val COLUMN_GLASS = "glass"
    const val COLUMN_SHADOW = "shadow"
    const val COLUMN_WALLPAPER = "wallpaper"
    const val COLUMN_BACKGROUND = "background"

    private val WALLPAPER = Regex("^content://${Regex.escape(AUTHORITY)}/$WALLPAPER_PATH/[A-Za-z0-9_-]{1,64}$")

    /**
     * The wallpaper reference if it is exactly `content://<AUTHORITY>/wallpaper/<id>` (id: 1-64 of
     * A-Z a-z 0-9 _ -; no query, fragment, user info, port, `..` or extra segments), else null. Only
     * the launcher's own provider can serve such a URI.
     */
    fun validWallpaper(value: String?): String? = value?.takeIf { WALLPAPER.matches(it) }

    /**
     * One provider row to a [SharedTheme], or null when it isn't shared or is malformed: the theme id
     * must be known and `shared` exactly 1. Glass and Shadow are on only for exactly 1 (anything else
     * or missing = off); an invalid wallpaper is dropped; a missing or unknown background mode is
     * [BackgroundMode.DEFAULT]. Fail closed throughout.
     */
    fun parse(
        themeId: String?, shared: Long?, glass: Long?, shadow: Long?, wallpaper: String?,
        background: String? = null,
    ): SharedTheme? {
        if (shared != 1L) return null
        val theme = ChromekitThemes.find(themeId) ?: return null
        return SharedTheme(theme.id, glass == 1L, shadow == 1L, validWallpaper(wallpaper), BackgroundMode.parse(background))
    }
}

/**
 * What an app draws behind its UI. [NONE]: pure black. [THEME]: the theme's `bg` (a very dark tint of
 * its main colour). [DEFAULT]: the theme's bundled wallpaper (over `bg`). [IMPORT]: the user's own
 * wallpaper, read through [SharedTheme.wallpaperUri]; without a usable one an app shows its default
 * wallpaper instead.
 */
enum class BackgroundMode(val value: String) {
    NONE("none"), THEME("theme"), DEFAULT("default"), IMPORT("import");

    companion object {
        /** Exact, case-sensitive match on [value]; anything else (or null) is [DEFAULT]. */
        fun parse(value: String?): BackgroundMode = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** The launcher's shared look, already validated. */
data class SharedTheme(
    val themeId: String,
    val glass: Boolean,
    val shadow: Boolean,
    val wallpaperUri: String?,
    val background: BackgroundMode = BackgroundMode.DEFAULT,
)

/**
 * Placeholder, NOT a real certificate hash. TODO(Oni3xe): replace with the SHA-256 of
 * cybr-launcher's release signing certificate (64 hex chars, e.g. from `apksigner verify
 * --print-certs`). While this placeholder is pinned, the launcher is never trusted and every app
 * uses its local theme (fail closed).
 */
const val LAUNCHER_CERT_SHA256_PLACEHOLDER = "TODO_PIN_CYBR_LAUNCHER_RELEASE_CERT_SHA256"

/** The pinned launcher signing-cert SHA-256. */
const val LAUNCHER_CERT_SHA256: String = LAUNCHER_CERT_SHA256_PLACEHOLDER

/** Pure trust rules, unit tested. */
object LauncherTrust {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** A usable pin is 64 hex chars (colons and case ignored); anything else, like the placeholder, is null. */
    fun normalizePin(pin: String): String? = pin.replace(":", "").lowercase().takeIf { HEX64.matches(it) }

    /**
     * Trusted only if the provider's package is the launcher AND it has exactly one current signing
     * cert whose SHA-256 is the pinned hash. A placeholder or malformed pin, a missing provider, no
     * cert or several signers fails closed.
     */
    fun isTrusted(providerPackage: String?, certSha256s: List<ByteArray>, pin: String = LAUNCHER_CERT_SHA256): Boolean {
        val pinned = normalizePin(pin) ?: return false
        if (providerPackage != LauncherThemeContract.LAUNCHER_PACKAGE) return false
        return certSha256s.size == 1 && hex(certSha256s[0]) == pinned
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}

/** Reads the launcher's shared look, or null when it isn't trusted, isn't shared or can't be read. */
class LauncherThemeClient(private val context: Context, private val pin: String = LAUNCHER_CERT_SHA256) {

    /** True only when the provider resolves to the pinned launcher (re-checked on every read). */
    private fun trusted(): Boolean {
        if (LauncherTrust.normalizePin(pin) == null) return false // placeholder pin: fail closed, no IPC
        if (context.packageName == LauncherThemeContract.LAUNCHER_PACKAGE) return false
        val pm = context.packageManager
        val provider = runCatching { pm.resolveContentProvider(LauncherThemeContract.AUTHORITY, 0) }.getOrNull() ?: return false
        return LauncherTrust.isTrusted(provider.packageName, certHashes(pm, provider.packageName), pin)
    }

    fun readShared(): SharedTheme? {
        if (!trusted()) return null
        return runCatching {
            context.contentResolver.query(LauncherThemeContract.URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                LauncherThemeContract.parse(
                    c.str(LauncherThemeContract.COLUMN_THEME), c.long(LauncherThemeContract.COLUMN_SHARED),
                    c.long(LauncherThemeContract.COLUMN_GLASS), c.long(LauncherThemeContract.COLUMN_SHADOW),
                    c.str(LauncherThemeContract.COLUMN_WALLPAPER),
                    c.str(LauncherThemeContract.COLUMN_BACKGROUND),
                )
            }
        }.getOrNull()
    }

    /**
     * Opens the launcher's shared wallpaper read-only, or null. Only a URI that passes
     * [LauncherThemeContract.validWallpaper] and only while the launcher is trusted. Call off the
     * main thread; the caller closes the stream.
     */
    fun openWallpaper(uri: String?): InputStream? {
        val valid = LauncherThemeContract.validWallpaper(uri) ?: return null
        if (!trusted()) return null
        return runCatching { context.contentResolver.openInputStream(Uri.parse(valid)) }.getOrNull()
    }

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || getType(i) != Cursor.FIELD_TYPE_STRING) null else getString(i)
    }

    private fun Cursor.long(col: String): Long? {
        val i = getColumnIndex(col)
        return if (i < 0 || getType(i) != Cursor.FIELD_TYPE_INTEGER) null else getLong(i)
    }

    @Suppress("DEPRECATION")
    private fun certHashes(pm: PackageManager, pkg: String): List<ByteArray> = runCatching {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            // Current signers only; a rotated key's past certs are not trusted.
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        sigs.orEmpty().map { LauncherTrust.sha256(it.toByteArray()) }
    }.getOrDefault(emptyList())
}
