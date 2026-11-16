package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

/** What the main process does when the engine does not keep to the protocol. */
class WireCollectorTrustTest {
    private val name = RawPath.of("a".toByteArray())
    private val zip = WireArchive(ArchiveKind.ZIP, false, Encryption.NONE, null, false, false)

    private fun wire(block: WireWriter.() -> Unit) = ByteArrayOutputStream().also { WireWriter(it).use(block) }.toByteArray()
    private fun item(index: Long, path: ByteArray = "a".toByteArray(), type: EntryType = EntryType.FILE) =
        WireEntry(index, RawPath.of(path), true, type, 1, null, null, false, null, null, null)
    private fun events(bytes: ByteArray, expected: Map<Long, RawPath> = mapOf(0L to name)) =
        WireCollector.events(WireReader(bytes.inputStream()), expected).toList()

    @Test fun entriesOutOfOrder_areRefused() {
        val bytes = wire { entry(item(1)); entry(item(1)); archive(zip); status(EngineStatus.OK) }
        assertThrows(WireFormatException::class.java) { WireCollector.index(WireReader(bytes.inputStream()), ArchiveKind.ZIP) }
    }

    @Test fun aNameOverTheLimit_isSkippedAndOnlyItsHeadIsKept() {
        val long = ByteArray(Limits.MAX_PATH_BYTES + 100) { 'n'.code.toByte() }
        val bytes = wire { entry(item(0, long)); archive(zip); status(EngineStatus.OK) }
        val kept = WireCollector.index(WireReader(bytes.inputStream()), ArchiveKind.ZIP).index!!.entries.single()
        assertThat(kept.path).isEqualTo(SafePath.Result.Skipped(SkipReason.INVALID_NAME))
        assertThat(kept.rawPath.size).isEqualTo(Limits.MAX_PATH_BYTES)
    }

    @Test fun progressIsReported() {
        var seen = 0
        val bytes = wire { repeat(1_100) { entry(item(it.toLong())) }; archive(zip); status(EngineStatus.OK) }
        WireCollector.index(WireReader(bytes.inputStream()), ArchiveKind.ZIP) { seen = it }
        assertThat(seen).isEqualTo(1_024)
    }

    @Test fun bytesOutsideAnEntry_areRefused() {
        val bytes = wire { data(byteArrayOf(1), 0, 1); status(EngineStatus.OK) }
        assertThrows(WireFormatException::class.java) { events(bytes) }
    }

    @Test fun anEntryStartedTwice_isRefused() {
        val bytes = wire { dataStart(0, name); dataEnd(0, EntryOutcome.OK); dataStart(0, name); status(EngineStatus.OK) }
        assertThrows(WireFormatException::class.java) { events(bytes) }
    }

    @Test fun anEntryThatWasNotAskedFor_endsTheStream() {
        val bytes = wire { dataStart(5, name); data(byteArrayOf(1), 0, 1); status(EngineStatus.OK) }
        assertThat(events(bytes)).containsExactly(EngineEvent.Finished(EngineStatus.PATH_MISMATCH))
    }

    @Test fun aStatusInTheMiddleOfAnEntry_closesTheEntryAsDamaged() {
        val bytes = wire { dataStart(0, name); data(byteArrayOf(1), 0, 1); status(EngineStatus.DAMAGED) }
        val got = events(bytes)
        assertThat(got.takeLast(2)).containsExactly(
            EngineEvent.EntryFinished(0, EntryOutcome.DAMAGED), EngineEvent.Finished(EngineStatus.DAMAGED),
        ).inOrder()
    }

    @Test fun nothingAfterTheStatus_isRead() {
        val bytes = wire { status(EngineStatus.OK) } + byteArrayOf(99, 99, 99)
        val reader = WireReader(bytes.inputStream())
        assertThat(reader.next()).isEqualTo(WireMessage.Status(EngineStatus.OK))
        assertThat(reader.next()).isNull()
    }
}
