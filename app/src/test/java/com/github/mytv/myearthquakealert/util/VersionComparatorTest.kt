package com.github.mytv.myearthquakealert.util

import org.junit.Assert.*
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun `isNewerVersion returns true for higher major version`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `isNewerVersion returns false for same version`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns true for higher minor version`() {
        assertTrue(isNewerVersion("1.2.0", "1.1.9"))
    }

    @Test
    fun `isNewerVersion returns true for higher patch version`() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `isNewerVersion handles missing patch version`() {
        assertTrue(isNewerVersion("1.1", "1.0.5"))
    }

    @Test
    fun `isNewerVersion returns false for older version`() {
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
    }
}
