package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import org.junit.Test
import java.io.ByteArrayOutputStream

class WireCollectorTest {
    private fun wire(block: WireWriter.() -> Unit) = ByteArrayOutputStream().also { WireWriter(it).use(block) }.toByteArray()
    private fun e(i: Long, p: String, enc: Boolean = false, utf8: Boolean = true, raw: ByteArray = p.toByteArray()) =
        WireEntry(i, RawPath.of(raw), utf8, EntryType.FILE, 1, null, null, enc, null, null, HostOs.UNIX)
    private fun archive(kind: ArchiveKind = ArchiveKind.ZIP, encryption: Encryption = Encryption.NONE) =
        WireArchive(kind, false, encryption, null, false, false)
    private fun collect(bytes: ByteArray, hint: ArchiveKind = ArchiveKind.ZIP) = WireCollector.index(WireReader(bytes.inputStream()), hint)

    @Test fun buildsTheIndexWithSafePathsAndEncryption() {
        val bytes = wire {
            entry(e(0, "docs/a.md")); entry(e(1, "../x", enc = true))
            archive(WireArchive(ArchiveKind.ZIP, false, Encryption.SOME_ENTRIES, "c".toByteArray(), false, false)); status(EngineStatus.OK)
        }
        val got = collect(bytes)
        assertThat(got.status).isEqualTo(EngineStatus.OK)
        val idx = got.index!!
        assertThat(idx.formatLabel).isEqualTo("ZIP"); assertThat(idx.comment).isEqualTo("c"); assertThat(idx.createdBy).isEqualTo(HostOs.UNIX)
        assertThat(idx.entries[1].path).isEqualTo(SafePath.Result.Skipped(SkipReason.OUTSIDE_FOLDER))
        assertThat(idx.encryption).isEqualTo(Encryption.SOME_ENTRIES)
    }
    @Test fun allFilesEncrypted_isAllEntries() {
        val bytes = wire { entry(e(0, "a", enc = true)); entry(e(1, "b", enc = true)); archive(archive(encryption = Encryption.SOME_ENTRIES)); status(EngineStatus.OK) }
        assertThat(collect(bytes).index!!.encryption).isEqualTo(Encryption.ALL_ENTRIES)
    }
    @Test fun encryptedHeaders_stayEncryptedHeaders() {
        val bytes = wire { entry(e(0, "a")); archive(archive(encryption = Encryption.HEADERS)); status(EngineStatus.OK) }
        assertThat(collect(bytes).index!!.encryption).isEqualTo(Encryption.HEADERS)
    }
    @Test fun cp437NamesInZip_areDecoded() {
        val bytes = wire { entry(e(0, "", utf8 = false, raw = byteArrayOf(0x63, 0x61, 0x66, 0x82.toByte()))); archive(archive()); status(EngineStatus.OK) }
        val entry = collect(bytes).index!!.entries.single()
        assertThat(entry.displayPath).isEqualTo("café")
        assertThat(entry.pathIsUtf8).isFalse()
    }
    @Test fun unknownKindFromEngine_keepsTheHint() {
        val bytes = wire { entry(e(0, "a")); archive(archive(kind = ArchiveKind.UNKNOWN)); status(EngineStatus.OK) }
        assertThat(collect(bytes, hint = ArchiveKind.TAR).index!!.kind).isEqualTo(ArchiveKind.TAR)
    }
    @Test fun noArchiveMessage_meansNoIndex() {
        val got = collect(wire { status(EngineStatus.NOT_ARCHIVE) })
        assertThat(got.status).isEqualTo(EngineStatus.NOT_ARCHIVE)
        assertThat(got.index).isNull()
    }
    @Test fun events_mapMessagesAndRefuseAWrongPath() {
        val raw = RawPath.of("a".toByteArray())
        val bytes = wire { dataStart(0, raw); data(byteArrayOf(1, 2), 0, 2); progress(9); dataEnd(0, EntryOutcome.OK); status(EngineStatus.OK) }
        val events = WireCollector.events(WireReader(bytes.inputStream()), mapOf(0L to raw)).toList()
        assertThat(events.map { it::class.simpleName }).containsExactly("EntryStarted", "Bytes", "Progress", "EntryFinished", "Finished").inOrder()
        val wrong = WireCollector.events(WireReader(bytes.inputStream()), mapOf(0L to RawPath.of("b".toByteArray()))).toList()
        assertThat(wrong.last()).isEqualTo(EngineEvent.Finished(EngineStatus.PATH_MISMATCH))
    }
}
