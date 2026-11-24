package com.oni3xe.chromekit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTrustTest {
    private val cert = "launcher-release-cert".toByteArray()
    private val certHash = LauncherTrust.sha256(cert)
    private val pin = LauncherTrust.hex(certHash)
    private val launcher = LauncherThemeContract.LAUNCHER_PACKAGE

    @Test fun placeholderPinFailsClosed() {
        assertNull(LauncherTrust.normalizePin(LAUNCHER_CERT_SHA256_PLACEHOLDER))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(certHash), LAUNCHER_CERT_SHA256_PLACEHOLDER))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(certHash)))
    }

    @Test fun trustedWhenPackageAndCertMatch() {
        assertTrue(LauncherTrust.isTrusted(launcher, listOf(certHash), pin))
        val colons = pin.uppercase().chunked(2).joinToString(":")
        assertTrue(LauncherTrust.isTrusted(launcher, listOf(certHash), colons))
    }

    @Test fun impostorPackageOrCertIsRejected() {
        assertFalse(LauncherTrust.isTrusted("com.evil.cybrlauncher", listOf(certHash), pin))
        assertFalse(LauncherTrust.isTrusted(null, listOf(certHash), pin))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(LauncherTrust.sha256("other".toByteArray())), pin))
        assertFalse(LauncherTrust.isTrusted(launcher, emptyList(), pin))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(certHash, LauncherTrust.sha256("x".toByteArray())), pin))
    }

    @Test fun malformedPinsAreRejected() {
        assertNull(LauncherTrust.normalizePin(""))
        assertNull(LauncherTrust.normalizePin(pin.dropLast(1)))
        assertNull(LauncherTrust.normalizePin(pin.dropLast(1) + "g"))
        assertEquals(pin, LauncherTrust.normalizePin(pin.uppercase()))
    }
}
