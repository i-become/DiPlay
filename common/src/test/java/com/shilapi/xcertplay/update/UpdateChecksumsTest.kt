package com.shilapi.xcertplay.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateChecksumsTest {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test fun acceptsBomBinaryMarkerRelativePathAndBsdFormat() {
        for (text in listOf("\uFEFF$abc  DiPlay.apk\r\n", "$abc *DiPlay.apk", "$abc  ./DiPlay.apk",
                "$abc *./DiPlay.apk", "SHA256 (DiPlay.apk) = $abc")) {
            assertTrue(text, UpdateChecksums.matches(UpdateChecksums.parse(text), "DiPlay.apk", abc))
        }
    }

    @Test(expected = java.io.IOException::class)
    fun refusesConflictingEntriesInsteadOfDependingOnLineOrder() {
        UpdateChecksums.parse("$abc  DiPlay.apk\n${"0".repeat(64)} *./DiPlay.apk")
    }
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test fun readsWindowsUtf16AndUtf8ChecksumFiles() {
        val file = temporaryFolder.newFile("SHA256SUMS.txt")
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            file.writeBytes("\uFEFF$abc *DiPlay.apk\r\n".toByteArray(charset))
            assertTrue(UpdateChecksums.matches(UpdateChecksums.read(file), "DiPlay.apk", abc))
        }
    }

    @Test
    fun parsesChecksumLinesWithTwoSpaces() {
        val checksums = UpdateChecksums.parse("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  DiPlay-0.2.14.apk\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  SHA256SUMS.txt\n")
        assertEquals(
            mapOf(
                "DiPlay-0.2.14.apk" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                "SHA256SUMS.txt" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ),
            checksums,
        )
    }

    @Test
    fun ignoresBlankAndMalformedLines() {
        val checksums = UpdateChecksums.parse("\nnot-a-checksum-line\nshort  name.txt\n")
        assertTrue(checksums.isEmpty())
    }

    @Test
    fun hashesFileContentsStreamed() {
        val file = temporaryFolder.newFile("payload.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateChecksums.sha256Hex(file))
    }

    @Test
    fun matchesTheNamedEntryCaseInsensitively() {
        val checksums = mapOf("DiPlay-0.2.14.apk" to "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD")
        assertTrue(UpdateChecksums.matches(checksums, "DiPlay-0.2.14.apk", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
    }

    @Test
    fun rejectsUnknownFilesAndWrongDigests() {
        val checksums = mapOf("DiPlay-0.2.14.apk" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertFalse(UpdateChecksums.matches(checksums, "other.apk", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        assertFalse(UpdateChecksums.matches(checksums, "DiPlay-0.2.14.apk", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    }
}
