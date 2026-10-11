package de.moba.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemVerTest {
    @Test
    fun parsesAndCompares() {
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
        assertTrue(SemVer.parse("1.10.0") > SemVer.parse("1.9.3"))
    }

    @Test
    fun majorVersionMustMatch() {
        assertTrue(SemVer(1, 3).satisfies(SemVer(1, 2)))
        assertFalse(SemVer(1, 1).satisfies(SemVer(1, 2)))
        assertFalse(SemVer(2, 0).satisfies(SemVer(1, 2)))
    }
}
