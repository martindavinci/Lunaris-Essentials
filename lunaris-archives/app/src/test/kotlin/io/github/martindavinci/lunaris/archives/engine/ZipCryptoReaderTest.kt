package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.channels.FileChannel

class ZipCryptoReaderTest {
    private val password = "Lunaris-2026"

    /** Index in the listing (libarchive's order) and raw path of [name]. */
    private fun entry(name: String, fixture: String): Map<Long, RawPath> =
        FileChannel.open(Fixtures.file(fixture).toPath()).use { ch ->
            val order = ZipDirectory.read(ch).inReadOrder
            val i = order.indexOfFirst { String(it.name.bytes) == name }
            mapOf(i.toLong() to order[i].name)
        }
    private fun extract(fixture: String, name: String, password: String?, job: EngineJob = EngineJob(1)) =
        Fixtures.runExtract(ZipCryptoReader(), fixture, password, entry(name, fixture), job)
    private fun outcome(events: List<EngineEvent>) = events.filterIsInstance<EngineEvent.EntryFinished>().single().outcome

    @Test fun aesEntry_decryptsWithTheRightPassword() {
        val events = extract("aes.zip", "docs/secret.md", password)
        assertThat(Fixtures.contents(events).single()).isEqualTo("# Secret notes\n".toByteArray())
        assertThat(events.last()).isEqualTo(EngineEvent.Finished(EngineStatus.OK))
    }
    @Test fun aesOverDeflate_decrypts() {
        val big = Fixtures.contents(extract("aes.zip", "docs/big.md", password)).single()
        assertThat(String(big)).isEqualTo("lunaris archives test line\n".repeat(400))
    }
    @Test fun zipCryptoEntry_decrypts() {
        assertThat(Fixtures.contents(extract("zipcrypto.zip", "docs/secret.md", password)).single()).isEqualTo("# Secret notes\n".toByteArray())
    }
    @Test fun wrongPassword_isReportedPerEntry() {
        assertThat(outcome(extract("aes.zip", "docs/secret.md", "nope"))).isEqualTo(EntryOutcome.WRONG_PASSWORD)
    }
    @Test fun missingPassword_isEncrypted() {
        assertThat(outcome(extract("aes.zip", "docs/secret.md", null))).isEqualTo(EntryOutcome.ENCRYPTED)
    }
    @Test fun aesOverDeflate64_isUnsupported() {
        assertThat(outcome(extract("deflate64-aes.zip", "docs/big.md", password))).isEqualTo(EntryOutcome.UNSUPPORTED)
    }
    @Test fun plainEntryInAPlainZip_alsoWorks() {
        assertThat(Fixtures.contents(extract("plain.zip", "README.md", null)).single()).isEqualTo("# Project notes\n".toByteArray())
    }
    @Test fun cancelledJob_stops() {
        val job = EngineJob(9).apply { cancel() }
        assertThrows(CancellationException::class.java) { extract("aes.zip", "docs/secret.md", password, job) }
    }
    @Test fun listing_readsTheCentralDirectory() {
        val index = Fixtures.runList(ZipCryptoReader(), "aes.zip").index!!
        assertThat(index.entries.map { it.displayPath }).containsAtLeast("docs/secret.md", "readme.txt")
        assertThat(index.entries.first { it.displayPath == "readme.txt" }.encrypted).isTrue()
    }
}
