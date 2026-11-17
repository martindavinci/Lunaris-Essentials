package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FingerprintTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = Random(3).nextBytes(Limits.FINGERPRINT_EDGE_BYTES * 3)

    private fun of(data: ByteArray): String {
        val f = folder.newFile().apply { writeBytes(data) }
        return RandomAccessFile(f, "r").use { Fingerprint.of(it.channel, f.length()) }
    }

    @Test fun sameBytesInDifferentFiles_match() { assertThat(of(bytes)).isEqualTo(of(bytes.copyOf())) }
    @Test fun changedMiddleByte_sameEdges_matches_aKnownLimit() {
        val middle = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertThat(of(middle)).isEqualTo(of(bytes))
    }
    @Test fun changedLastByte_differs() {
        assertThat(of(bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })).isNotEqualTo(of(bytes))
    }
    @Test fun changedLength_differs() { assertThat(of(bytes.copyOf(bytes.size - 1))).isNotEqualTo(of(bytes)) }
    @Test fun smallFile_isHashedWhole() {
        assertThat(of(byteArrayOf(1, 2, 3))).isNotEqualTo(of(byteArrayOf(1, 2, 4)))
        assertThat(of(ByteArray(0))).hasLength(SHA256_HEX)
    }
    @Test fun sha256_ofAFile_isTheStandardDigest() {
        val f = File(folder.root, "abc").apply { writeText("abc") }
        assertThat(Fingerprint.sha256(f)).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    private companion object { const val SHA256_HEX = 64 }
}
