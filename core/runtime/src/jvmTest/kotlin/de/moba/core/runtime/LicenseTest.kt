package de.moba.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LicenseTest {
    private val keys = LicenseSigner.generateKeyPair()
    private val signer = LicenseSigner(keys.first)
    private val verifier = EcLicenseVerifier(keys.second)
    private val license = License("L-0001", "Max Mustermann", setOf("pro", "decoder"), "2026-10-13", "2027-10-12")

    @Test
    fun signedKeyIsValid() {
        val key = signer.sign(license)
        val check = verifier.check(key, "2026-10-13")
        assertIs<LicenseCheck.Valid>(check)
        assertEquals(license, check.license)
    }

    @Test
    fun pastedWithLineBreaksStillValid() {
        val key = signer.sign(license).chunked(40).joinToString("\n  ")
        assertIs<LicenseCheck.Valid>(verifier.check(key, "2026-10-13"))
    }

    @Test
    fun changedKeyIsRejected() {
        val key = signer.sign(license)
        val (payload, signature) = LicenseFormat.decode(key)!!
        val forged = LicenseFormat.encode(payload.decodeToString().replace("decoder,pro", "club,decoder,pro"), signature)
        assertIs<LicenseCheck.Invalid>(verifier.check(forged, "2026-10-13"))
    }

    @Test
    fun otherSignerIsRejected() {
        val other = LicenseSigner(LicenseSigner.generateKeyPair().first)
        assertIs<LicenseCheck.Invalid>(verifier.check(other.sign(license), "2026-10-13"))
    }

    @Test
    fun expiredKeyIsRejected() {
        val check = verifier.check(signer.sign(license), "2027-10-13")
        assertIs<LicenseCheck.Invalid>(check)
        assertTrue("abgelaufen" in check.reason)
    }

    @Test
    fun garbageAndMissingPublicKey() {
        assertIs<LicenseCheck.Invalid>(verifier.check("hallo", "2026-10-13"))
        assertIs<LicenseCheck.Invalid>(EcLicenseVerifier("").check(signer.sign(license), "2026-10-13"))
    }

    @Test
    fun featuresUnlockModules() {
        val info = LicenseInfo.FREE.plus(license)
        assertTrue(info.isUnlocked("pro"))
        assertTrue(info.isUnlocked("decoder"))
        assertFalse(info.isUnlocked("club"))
        assertTrue(info.isUnlocked("basis"))
        val club = LicenseInfo.FREE.plus(license.copy(features = setOf("club")))
        assertTrue(listOf("basis", "pro", "decoder", "club").all(club::isUnlocked))
        val basis = LicenseInfo.FREE.plus(license.copy(features = setOf("basis")))
        assertTrue(basis.isUnlocked("basis"))
        assertFalse(basis.isUnlocked("pro"))
        assertFalse(LicenseInfo.FREE.plus(null).isUnlocked("pro"))
    }
}
