package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Test

class NativeSinkTest {
    private val bytes = ByteArrayOutputStream()
    private val out = WireWriter(bytes)
    private val name = "a".toByteArray()

    private fun written(): List<WireMessage> {
        out.status(EngineStatus.OK)
        val reader = WireReader(bytes.toByteArray().inputStream())
        return generateSequence { reader.next() }.toList()
    }

    @Test fun zipEntriesWaitForTheCentralDirectory() {
        val sink = NativeSink(out, expected = null)
        sink.onArchive(ZIP, intArrayOf(0), 0)
        assertThat(sink.onEntry(0, "a.md".toByteArray(), true, REGULAR, 10, true, 5, true, false, null, false)).isTrue()
        assertThat(sink.buffered.single())
            .isEqualTo(WireEntry(0, RawPath.of("a.md".toByteArray()), true, EntryType.FILE, 10, null, 5_000, false, null, null, null))
        assertThat(written()).containsExactly(WireMessage.Status(EngineStatus.OK))
    }

    @Test fun entriesOfOtherFormatsGoOutAtOnce() {
        val sink = NativeSink(out, expected = null)
        sink.onArchive(TAR, intArrayOf(GZIP, 0), 0)
        sink.onEntry(0, "d/".toByteArray(), false, DIRECTORY, 0, false, 0, false, false, null, false)
        assertThat(sink.buffered).isEmpty()
        val entry = (written().first() as WireMessage.Entry).entry
        assertThat(entry.size).isNull()
        assertThat(entry.mtimeMillis).isNull()
    }

    @Test fun anEntryIsStartThenPositionThenDataThenEnd() {
        val sink = NativeSink(out, expected = mapOf(3L to RawPath.of(name)))
        assertThat(sink.onEntryStart(3, name, 2, true, 40)).isEqualTo(NativeSink.CONTINUE)
        sink.buffer.put(0, 7); sink.buffer.put(1, 8)
        assertThat(sink.onData(2, 42)).isEqualTo(NativeSink.CONTINUE)
        assertThat(sink.onEntryEnd(3, EntryOutcome.OK.code)).isTrue()
        val got = written()
        assertThat(got[0]).isEqualTo(WireMessage.DataStart(3, RawPath.of(name)))
        assertThat(got[1]).isEqualTo(WireMessage.Progress(40))           // where the entry starts: the ratio guard needs it
        assertThat((got[2] as WireMessage.Data).bytes).isEqualTo(byteArrayOf(7, 8))
        assertThat(got.drop(3)).containsExactly(WireMessage.Progress(42), WireMessage.DataEnd(3, EntryOutcome.OK), WireMessage.Status(EngineStatus.OK)).inOrder()
    }

    @Test fun anotherPathAtThatIndex_isAMismatch() {
        val sink = NativeSink(out, expected = mapOf(3L to RawPath.of(name)))
        assertThat(sink.onEntryStart(3, "b".toByteArray(), 1, true, 0)).isEqualTo(NativeSink.MISMATCH)
    }

    @Test fun moreThanDeclared_isALimit_notAStop() {
        val sink = NativeSink(out, expected = null)
        sink.onEntryStart(0, name, 0, true, 0)
        assertThat(sink.onData(Limits.OVERRUN_SLACK_BYTES.toInt(), 1)).isEqualTo(NativeSink.CONTINUE)
        assertThat(sink.onData(1, 1)).isEqualTo(NativeSink.LIMIT)
    }

    @Test fun aClosedPipe_isAStop_andNothingIsThrown() {
        val closed = object : OutputStream() {
            override fun write(b: Int) = throw IOException("EPIPE")
        }
        val sink = NativeSink(WireWriter(closed), expected = null)
        sink.onEntryStart(0, name, 1, true, 0)
        assertThat(sink.onProgress(1)).isFalse()                          // the flush meets the closed pipe
        assertThat(sink.onData(1, 1)).isEqualTo(NativeSink.STOP)
        assertThat(sink.onEntryEnd(0, EntryOutcome.OK.code)).isFalse()
        assertThat(sink.closed).isTrue()
    }

    @Test fun damagedBecauseEncrypted_isEncrypted_andTheNextEntryStartsClean() {
        val sink = NativeSink(out, expected = null)
        sink.onEntryStart(0, name, 1, true, 0)
        sink.onError(0, "Reading encrypted data is not currently supported".toByteArray())
        sink.onEntryEnd(0, EntryOutcome.DAMAGED.code)
        sink.onEntryStart(1, name, 1, true, 0)
        sink.onEntryEnd(1, EntryOutcome.DAMAGED.code)
        val ends = written().filterIsInstance<WireMessage.DataEnd>().map { it.outcome }
        assertThat(ends).containsExactly(EntryOutcome.ENCRYPTED, EntryOutcome.DAMAGED).inOrder()
    }

    @Test fun answersMatchTheCHeader() {
        val header = File("../native/jni/engine_status.h").readText()
        val c = Regex("""#define SINK_([A-Z_]+) (\d+)""").findAll(header).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertThat(c).containsExactly("CONTINUE", NativeSink.CONTINUE, "LIMIT", NativeSink.LIMIT, "MISMATCH", NativeSink.MISMATCH, "STOP", NativeSink.STOP)
    }

    private companion object {
        const val ZIP = 0x50000
        const val TAR = 0x30000
        const val GZIP = 1
        const val REGULAR = 0x8000
        const val DIRECTORY = 0x4000
    }
}
