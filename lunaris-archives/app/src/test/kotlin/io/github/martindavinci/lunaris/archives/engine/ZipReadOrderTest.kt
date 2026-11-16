package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.LocalHeader
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import java.io.ByteArrayOutputStream
import java.nio.channels.FileChannel
import org.junit.Test

/** An index is the place of an entry in libarchive's listing, and one call can extract several entries. */
class ZipReadOrderTest {
    private fun raw(text: String) = RawPath.of(text.toByteArray())

    private fun extract(fixture: String, password: String?, expected: Map<Long, RawPath>): List<EngineEvent> {
        val bytes = ByteArrayOutputStream()
        FileChannel.open(Fixtures.file(fixture).toPath()).use { channel ->
            WireWriter(bytes).use { out ->
                val indices = expected.keys.toLongArray()
                out.status(ZipCryptoReader().extract(EngineJob(1), EngineVolume(-1, channel), password?.toCharArray(), indices, null, out))
            }
        }
        return WireCollector.events(WireReader(bytes.toByteArray().inputStream()), expected).toList()
    }

    private fun content(events: List<EngineEvent>): List<String> {
        val files = ArrayList<StringBuilder>()
        events.forEach { event ->
            if (event is EngineEvent.EntryStarted) files += StringBuilder()
            if (event is EngineEvent.Bytes) files.last().append(String(event.chunk, 0, event.length))
        }
        return files.map { it.toString() }
    }

    @Test fun readOrder_isByLocalHeader_notByCentralDirectory() {
        FileChannel.open(Fixtures.file("reordered.zip").toPath()).use { channel ->
            val directory = ZipDirectory.read(channel)
            assertThat(directory.entries.map { String(it.name.bytes) }).containsExactly("third.txt", "second.txt", "first.txt").inOrder()
            assertThat(directory.inReadOrder.map { String(it.name.bytes) }).containsExactly("first.txt", "second.txt", "third.txt").inOrder()
            assertThat(LocalHeader.name(channel, directory.inReadOrder.first().localHeaderOffset)).isEqualTo(raw("first.txt"))
        }
    }

    /** Index 0 of a libarchive listing of this archive is first.txt, not the first entry of the directory. */
    @Test fun anIndexMeansThePlaceInTheListing() {
        val events = extract("reordered.zip", null, mapOf(0L to raw("first.txt"), 2L to raw("third.txt")))
        assertThat(events.last()).isEqualTo(EngineEvent.Finished(EngineStatus.OK))
        assertThat(content(events)).containsExactly("1st", "3rd").inOrder()
    }

    @Test fun severalEncryptedEntriesInOneCall_allCome() {
        val names = FileChannel.open(Fixtures.file("aes.zip").toPath()).use { channel ->
            ZipDirectory.read(channel).inReadOrder.map { String(it.name.bytes) }
        }
        val secret = names.indexOf("docs/secret.md").toLong()
        val readme = names.indexOf("readme.txt").toLong()
        val events = extract("aes.zip", "Lunaris-2026", mapOf(secret to raw("docs/secret.md"), readme to raw("readme.txt")))
        val outcomes = events.filterIsInstance<EngineEvent.EntryFinished>().map { it.outcome }
        assertThat(outcomes).containsExactly(EntryOutcome.OK, EntryOutcome.OK)
        assertThat(content(events)).containsExactly("# Secret notes\n", "plain readme\n")
    }

    @Test fun anIndexPastTheEnd_isAChangedArchive() {
        val events = extract("reordered.zip", null, mapOf(9L to raw("first.txt")))
        assertThat(events.last()).isEqualTo(EngineEvent.Finished(EngineStatus.PATH_MISMATCH))
    }
}
