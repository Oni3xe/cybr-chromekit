package com.oni3xe.chromekit.theme

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.security.MessageDigest

/**
 * The launcher's exported, read-only theme provider. One row, columns [COLUMN_THEME] (theme id) and
 * [COLUMN_SHARED] (1 when "Apply this theme to all cybr apps" is on). No write paths.
 */
object LauncherThemeContract {
    const val LAUNCHER_PACKAGE = "com.oni3xe.cybrlauncher"
    const val AUTHORITY = "$LAUNCHER_PACKAGE.chromekit.theme"
    const val PATH = "theme"
    val URI: Uri get() = Uri.parse("content://$AUTHORITY/$PATH")
    const val COLUMN_THEME = "theme_id"
    const val COLUMN_SHARED = "shared"
}

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

/** Reads the launcher's shared theme id, or null when it isn't trusted, isn't shared or can't be read. */
class LauncherThemeClient(private val context: Context, private val pin: String = LAUNCHER_CERT_SHA256) {

    fun readSharedThemeId(): String? {
        if (LauncherTrust.normalizePin(pin) == null) return null // placeholder pin: fail closed, no IPC
        if (context.packageName == LauncherThemeContract.LAUNCHER_PACKAGE) return null
        val pm = context.packageManager
        val provider = runCatching { pm.resolveContentProvider(LauncherThemeContract.AUTHORITY, 0) }.getOrNull() ?: return null
        if (!LauncherTrust.isTrusted(provider.packageName, certHashes(pm, provider.packageName), pin)) return null
        return runCatching {
            context.contentResolver.query(LauncherThemeContract.URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val shared = c.getColumnIndex(LauncherThemeContract.COLUMN_SHARED)
                val theme = c.getColumnIndex(LauncherThemeContract.COLUMN_THEME)
                if (shared < 0 || theme < 0 || c.getInt(shared) != 1) null
                else ChromekitThemes.find(c.getString(theme))?.id
            }
        }.getOrNull()
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
