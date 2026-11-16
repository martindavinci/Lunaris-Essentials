package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import org.junit.Test
import java.security.MessageDigest

/**
 * Fixtures of the vendored lib-rar (rar723/manifest.json): "encrypted-headers.rar" (rar -hp) and
 * "encrypted-data.rar" (rar -p), password "password", both holding hello.txt (12 bytes, SHA-256
 * 87699afe…1619); "symlinks.rar" with the symlink "to-target"; libarchive's
 * "test_read_format_rar.rar" is a plain RAR4 archive.
 */
class RarReader5Test {
    private val password = "password"
    private val headers = Fixtures.libRar("rar723/encrypted-headers.rar")
    private val data = Fixtures.libRar("rar723/encrypted-data.rar")
    private fun list(file: java.io.File, password: String?) = Fixtures.runList(RarReader5(), file, password)
    private fun extractHello(password: String?): List<EngineEvent> {
        val hello = list(data, this.password).index!!.entries.first { it.displayPath == "hello.txt" }
        return Fixtures.runExtract(RarReader5(), data, password, mapOf(hello.index to hello.rawPath))
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun encryptedHeaders_listWithThePassword() {
        val got = list(headers, password)
        assertThat(got.status).isEqualTo(EngineStatus.OK)
        assertThat(got.index!!.entries.map { it.displayPath }).contains("hello.txt")
        assertThat(got.index!!.encryption).isEqualTo(Encryption.HEADERS)
    }
    @Test fun encryptedHeaders_withoutPassword_needPassword() {
        assertThat(list(headers, null).status).isEqualTo(EngineStatus.HEADERS_ENCRYPTED)
    }
    @Test fun encryptedHeaders_wrongPassword() {
        assertThat(list(headers, "nope").status).isEqualTo(EngineStatus.WRONG_PASSWORD)
    }
    @Test fun encryptedData_listsWithoutPasswordAndExtractsWithIt() {
        val index = list(data, null).index!!
        assertThat(index.entries.first { it.displayPath == "hello.txt" }.encrypted).isTrue()
        val content = Fixtures.contents(extractHello(password)).single()
        assertThat(content.size).isEqualTo(12)
        assertThat(sha256(content)).isEqualTo("87699afe70c5e4275bf83c37dac2cf84ab9745e0ce8d8b06577da7e0b71c1619")
    }
    @Test fun encryptedData_wrongPassword_isAnEntryOutcome() {
        val outcome = extractHello("nope").filterIsInstance<EngineEvent.EntryFinished>().single().outcome
        assertThat(outcome).isEqualTo(EntryOutcome.WRONG_PASSWORD)
    }
    @Test fun symlinks_areSymlinks() {
        val entries = list(Fixtures.libRar("rar723/symlinks.rar"), null).index!!.entries
        assertThat(entries.first { it.displayPath == "to-target" }.type).isEqualTo(EntryType.SYMLINK)
    }
    @Test fun rar4_isReportedAsTheUnsupportedProtectedKind() {
        assertThat(list(Fixtures.libRar("libarchive/test_read_format_rar.rar"), null).status).isEqualTo(EngineStatus.RAR4_ENCRYPTED)
    }
}
