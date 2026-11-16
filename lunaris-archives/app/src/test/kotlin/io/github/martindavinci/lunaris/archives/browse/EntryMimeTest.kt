package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntryMimeTest {
    private val platform = mapOf("png" to "image/png", "csv" to "text/comma-separated-values", "pdf" to "application/pdf")
    private fun mime(name: String, head: ByteArray? = null) = EntryMime.of(name, head) { platform[it] }

    @Test fun ourTableComesFirst() {
        assertThat(mime("README.md")).isEqualTo("text/markdown")
        assertThat(mime("Main.kt")).isEqualTo("text/x-kotlin")
        assertThat(mime("data.csv")).isEqualTo("text/csv")
        assertThat(mime("conf.yaml")).isEqualTo("application/x-yaml")
        assertThat(mime("app.toml")).isEqualTo("text/plain")
        assertThat(mime("inner.7z")).isEqualTo("application/x-7z-compressed")
        assertThat(mime("backup.TGZ")).isEqualTo("application/gzip")
    }
    @Test fun thenThePlatform() { assertThat(mime("cover.PNG")).isEqualTo("image/png") }
    @Test fun unknownTextIsText_binaryIsOctetStream() {
        assertThat(mime("LICENSE", "plain words".toByteArray())).isEqualTo("text/plain")
        assertThat(mime("notes.unknown", "café".toByteArray())).isEqualTo("text/plain")
        assertThat(mime("data.bin", byteArrayOf(0, 1, 2))).isEqualTo("application/octet-stream")
        assertThat(mime("data.bin", byteArrayOf(0x61, 0x00, 0x62))).isEqualTo("application/octet-stream")
        assertThat(mime("data.bin", byteArrayOf(0x61, 0x82.toByte()))).isEqualTo("application/octet-stream")
        assertThat(mime("data.bin", null)).isEqualTo("application/octet-stream")
    }
    @Test fun aCutMultiByteCharacterAtTheEndOfTheSample_isStillText() {
        val sample = "a".repeat(10) + "é"
        val bytes = sample.toByteArray()
        assertThat(mime("x", bytes.copyOf(bytes.size - 1))).isEqualTo("text/plain")
    }
}
