package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class EngineWireTest {
    private val entry = WireEntry(7, RawPath.of("docs/a.md".toByteArray()), true, EntryType.FILE, 10, 8, 1_000, false, 0xABCDL, null, HostOs.UNIX)
    private val archive = WireArchive(ArchiveKind.GZIP, innerTar = true, Encryption.NONE, comment = "hi".toByteArray(), capped = false, partial = false)

    private fun roundTrip(write: WireWriter.() -> Unit): List<WireMessage> {
        val out = ByteArrayOutputStream()
        WireWriter(out).use { it.write() }
        val reader = WireReader(out.toByteArray().inputStream())
        return generateSequence { reader.next() }.toList()
    }

    private fun raw(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(EngineWire.VERSION); write() }.flush() }.toByteArray()

    @Test fun everyMessageRoundTrips() {
        val chunk = ByteArray(5) { it.toByte() }
        val got = roundTrip {
            archive(archive); entry(entry); dataStart(7, entry.rawPath); data(chunk, 1, 3); progress(99)
            dataEnd(7, EntryOutcome.OK); status(EngineStatus.OK)
        }
        assertThat(got.take(3)).containsExactly(WireMessage.Archive(archive), WireMessage.Entry(entry), WireMessage.DataStart(7, entry.rawPath)).inOrder()
        val data = got[3] as WireMessage.Data
        assertThat(data.bytes.copyOf(data.length)).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(got.drop(4)).containsExactly(WireMessage.Progress(99), WireMessage.DataEnd(7, EntryOutcome.OK), WireMessage.Status(EngineStatus.OK)).inOrder()
    }
    @Test fun nullablesRoundTrip() {
        val bare = entry.copy(size = null, packedSize = null, mtimeMillis = null, crc = null, host = null,
                              linkTarget = RawPath.of("../x".toByteArray()), type = EntryType.SYMLINK)
        assertThat(roundTrip { entry(bare); status(EngineStatus.OK) }.first()).isEqualTo(WireMessage.Entry(bare))
        val noComment = archive.copy(comment = null)
        assertThat(roundTrip { archive(noComment); status(EngineStatus.OK) }.first()).isEqualTo(WireMessage.Archive(noComment))
    }
    @Test fun everyKindRoundTripsByItsCode() {
        ArchiveKind.entries.forEach { kind ->
            val a = archive.copy(kind = kind)
            assertThat(roundTrip { archive(a); status(EngineStatus.OK) }.first()).isEqualTo(WireMessage.Archive(a))
        }
    }
    @Test fun truncatedStream_isAnError() {
        val out = ByteArrayOutputStream()
        WireWriter(out).use { it.entry(entry) }
        val bytes = out.toByteArray()
        val reader = WireReader(bytes.copyOf(bytes.size - 3).inputStream())
        assertThrows(WireFormatException::class.java) { generateSequence { reader.next() }.toList() }
    }
    @Test fun endWithoutStatus_isAnError() {
        val out = ByteArrayOutputStream()
        WireWriter(out).use { it.entry(entry) }
        val reader = WireReader(out.toByteArray().inputStream())
        reader.next()
        assertThrows(WireFormatException::class.java) { reader.next() }
    }
    @Test fun nothingIsReadAfterStatus() {
        val reader = WireReader(raw { writeByte(EngineWire.TAG_STATUS); writeByte(0); writeByte(99) }.inputStream())
        assertThat(reader.next()).isEqualTo(WireMessage.Status(EngineStatus.OK))
        assertThat(reader.next()).isNull()
    }
    @Test fun wrongVersion_isAnError() {
        assertThrows(WireFormatException::class.java) { WireReader(byteArrayOf(99).inputStream()).next() }
    }
    @Test fun unknownTagOrCode_isAnError() {
        assertThrows(WireFormatException::class.java) { WireReader(raw { writeByte(42) }.inputStream()).next() }
        assertThrows(WireFormatException::class.java) { WireReader(raw { writeByte(EngineWire.TAG_STATUS); writeByte(200) }.inputStream()).next() }
        assertThrows(WireFormatException::class.java) { WireReader(raw { writeByte(EngineWire.TAG_ARCHIVE); writeByte(200) }.inputStream()).next() }
    }
    @Test fun oversizedPath_isAnError() {
        val bytes = raw { writeByte(EngineWire.TAG_DATA_START); writeLong(1); writeInt(Int.MAX_VALUE) }
        assertThrows(WireFormatException::class.java) { WireReader(bytes.inputStream()).next() }
    }
    @Test fun badChunkLength_isAnError() {
        listOf(0, -1, EngineWire.CHUNK_BYTES + 1).forEach { length ->
            val bytes = raw { writeByte(EngineWire.TAG_DATA); writeInt(length) }
            assertThrows(WireFormatException::class.java) { WireReader(bytes.inputStream()).next() }
        }
    }
}
