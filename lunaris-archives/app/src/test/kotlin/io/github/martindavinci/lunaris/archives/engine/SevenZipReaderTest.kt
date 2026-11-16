package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import io.github.martindavinci.lunaris.archives.testing.SevenZipBytes
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SevenZipReaderTest {
    private val password = "Lunaris-2026"
    private fun list(name: String, password: String?) = Fixtures.runList(SevenZipReader(), name, password)
    private fun extract(name: String, password: String?, path: String): List<EngineEvent> {
        val entry = list(name, this.password).index!!.entries.first { it.displayPath == path }
        return Fixtures.runExtract(SevenZipReader(), name, password, mapOf(entry.index to entry.rawPath))
    }
    private fun outcome(events: List<EngineEvent>) = events.filterIsInstance<EngineEvent.EntryFinished>().single().outcome

    @Test fun encryptedHeaders_listWithThePassword() {
        val got = list("head-enc.7z", password)
        assertThat(got.status).isEqualTo(EngineStatus.OK)
        assertThat(got.index!!.entries.map { it.displayPath }).containsAtLeast("docs/secret.md", "readme.txt")
        assertThat(got.index!!.encryption).isEqualTo(Encryption.HEADERS)
    }
    @Test fun encryptedHeaders_withoutPassword_needPassword() {
        assertThat(list("head-enc.7z", null).status).isEqualTo(EngineStatus.HEADERS_ENCRYPTED)
    }
    @Test fun wrongPasswordOnHeaders_isWrongPassword() {
        assertThat(list("head-enc.7z", "nope").status).isEqualTo(EngineStatus.WRONG_PASSWORD)
    }
    @Test fun encryptedData_listsWithoutAPasswordAndExtractsWithIt() {
        val index = list("data-enc.7z", null).index!!
        assertThat(index.entries.map { it.displayPath }).containsAtLeast("docs/secret.md", "readme.txt")
        assertThat(Fixtures.contents(extract("data-enc.7z", password, "docs/secret.md")).single()).isEqualTo("# Secret notes\n".toByteArray())
    }
    @Test fun wrongPasswordOnData_isWrongPasswordOutcome() {
        assertThat(outcome(extract("data-enc.7z", "nope", "docs/secret.md"))).isEqualTo(EntryOutcome.WRONG_PASSWORD)
    }
    @Test fun ppmdInsideAes_isUnsupported() {
        assertThat(list("ppmd-enc.7z", password).status).isEqualTo(EngineStatus.OK)
        assertThat(outcome(extract("ppmd-enc.7z", password, "docs/big.md"))).isEqualTo(EntryOutcome.UNSUPPORTED)
    }
    @Test fun hugeKdfCost_isRefusedBeforeCommonsCompressRuns() {
        val file = SevenZipBytes.withAes(30)
        val copy = Fixtures.file("plain.zip").absoluteFile.resolveSibling("kdf-30.7z")
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
            assertThat(list("kdf-30.7z", password).status).isEqualTo(EngineStatus.UNSUPPORTED_FEATURE)
        } finally {
            copy.delete()
        }
    }
}
