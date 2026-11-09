package com.github.mytv.myearthquakealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SignatureMatchTest {

    @Test
    fun `same signer matches`() {
        assertTrue(SignatureMatch.matches(SignerSet(setOf("a")), SignerSet(setOf("a"))))
    }

    @Test
    fun `different signer does not match`() {
        assertFalse(SignatureMatch.matches(SignerSet(setOf("a")), SignerSet(setOf("b"))))
    }

    @Test
    fun `a rotated key whose lineage contains the installed certificate matches`() {
        assertTrue(SignatureMatch.matches(SignerSet(setOf("a"), listOf("a")), SignerSet(setOf("b"), listOf("a", "b"))))
        assertFalse(SignatureMatch.matches(SignerSet(setOf("a"), listOf("a")), SignerSet(setOf("c"), listOf("b", "c"))))
    }

    @Test
    fun `multiple signers must be the same set`() {
        assertTrue(SignatureMatch.matches(SignerSet(setOf("a", "b")), SignerSet(setOf("b", "a"))))
        assertFalse(SignatureMatch.matches(SignerSet(setOf("a", "b")), SignerSet(setOf("a"))))
    }

    @Test
    fun `missing certificates never match`() {
        assertFalse(SignatureMatch.matches(SignerSet(emptySet()), SignerSet(emptySet())))
        assertFalse(SignatureMatch.matches(SignerSet(setOf("a")), SignerSet(emptySet())))
    }

    @Test
    fun `sha256 of a file and of bytes agree with a known vector`() {
        val file = Files.createTempFile("sha", ".bin").toFile()
        try {
            file.writeText("abc")
            val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            assertEquals(expected, Sha256.of(file))
            assertEquals(expected, Sha256.of("abc".toByteArray()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `update files are cleaned up except the kept version`() {
        val dir = Files.createTempDirectory("upd").toFile()
        try {
            val files = UpdateFiles(dir)
            listOf(files.apk(5), files.part(9), files.meta(9), files.apk(12), files.part(12), File(dir, "junk.tmp")).forEach { it.writeText("x") }
            files.cleanup(installedCode = 8, keep = 12)
            assertEquals(setOf(files.apk(12).name, files.part(12).name), dir.listFiles()!!.map { it.name }.toSet())
            files.cleanup(installedCode = 8, keep = null)
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
