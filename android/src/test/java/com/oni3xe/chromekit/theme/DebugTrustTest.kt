package com.oni3xe.chromekit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTrustTest {
    private val launcher = LauncherThemeContract.LAUNCHER_PACKAGE
    /** A cert whose hash we know, standing in for the box debug key (the real pin is a hash we can't invert). */
    private val debugCert = LauncherTrust.sha256("box-debug-cert".toByteArray())
    private val debugPin = LauncherTrust.hex(debugCert)

    @Test fun realDebugPinIsWellFormed() {
        assertEquals("cae5089a2988df0d0eefd5e17a8953b66974dae5855815f03cfbf58099c08772", LAUNCHER_DEBUG_CERT_SHA256)
        assertNotNull(LauncherTrust.normalizePin(LAUNCHER_DEBUG_CERT_SHA256))
    }

    @Test fun debugPinAcceptedOnlyWithAllowDebug() {
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(debugCert), LAUNCHER_CERT_SHA256_PLACEHOLDER, allowDebug = false, debugPin = debugPin))
        assertTrue(LauncherTrust.isTrusted(launcher, listOf(debugCert), LAUNCHER_CERT_SHA256_PLACEHOLDER, allowDebug = true, debugPin = debugPin))
        // Default arguments: release behaviour, debug never trusted
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(debugCert), debugPin = debugPin))
    }

    @Test fun releasePlaceholderRejectsEverything() {
        assertTrue(LauncherTrust.acceptedPins(LAUNCHER_CERT_SHA256, allowDebug = false).isEmpty())
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(debugCert)))
        assertEquals(setOf(LAUNCHER_DEBUG_CERT_SHA256), LauncherTrust.acceptedPins(LAUNCHER_CERT_SHA256, allowDebug = true))
    }

    @Test fun debugTrustStillPinsPackageAndSingleSigner() {
        assertFalse(LauncherTrust.isTrusted("com.evil", listOf(debugCert), LAUNCHER_CERT_SHA256_PLACEHOLDER, true, debugPin))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(debugCert, debugCert), LAUNCHER_CERT_SHA256_PLACEHOLDER, true, debugPin))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(LauncherTrust.sha256("x".toByteArray())), LAUNCHER_CERT_SHA256_PLACEHOLDER, true, debugPin))
    }

    @Test fun releasePinStillWorksWithOrWithoutDebug() {
        val rel = LauncherTrust.sha256("release".toByteArray())
        val pin = LauncherTrust.hex(rel)
        assertTrue(LauncherTrust.isTrusted(launcher, listOf(rel), pin, false, debugPin))
        assertTrue(LauncherTrust.isTrusted(launcher, listOf(rel), pin, true, debugPin))
        assertFalse(LauncherTrust.isTrusted(launcher, listOf(debugCert), pin, false, debugPin))
    }
}
