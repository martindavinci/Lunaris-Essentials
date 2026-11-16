package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel

/** The archive as the engine process sees it: the raw descriptor for C, a channel for Kotlin. */
class EngineVolume(val fd: Int, val channel: FileChannel)

/** One per route; runs inside the isolated :engine process. Never keeps [password]. */
interface IsolatedReader {
    fun list(job: EngineJob, volume: EngineVolume, password: CharArray?, out: WireWriter): EngineStatus

    fun extract(
        job: EngineJob, volume: EngineVolume, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>?, out: WireWriter,
    ): EngineStatus
}

/**
 * The main process closed its end of the pipe. Not an IOException on purpose: readers catch
 * IOException to tell a damaged archive from a wrong password, and a closed pipe is neither.
 */
class SinkClosedException : RuntimeException("sink closed")

/** Writes to the pipe; a failure there ends the job instead of looking like an archive error. */
internal inline fun WireWriter.send(block: WireWriter.() -> Unit) {
    try {
        block()
    } catch (_: IOException) {
        throw SinkClosedException()
    }
}

/** Copies the bytes of one entry to the wire, in whole chunks. */
internal object EntryPump {
    /**
     * Read errors of [input] are thrown to the caller, which knows what they mean for its format.
     * [position] is where the reader is in the archive, for the ratio guard of the main process.
     */
    fun copy(input: InputStream, limit: Long, out: WireWriter, position: () -> Long): EntryOutcome {
        val buffer = ByteArray(EngineWire.CHUNK_BYTES)
        var total = 0L
        while (true) {
            val n = fill(input, buffer)
            if (n == 0) return EntryOutcome.OK
            total += n
            if (total > limit) return EntryOutcome.LIMIT
            out.send { data(buffer, 0, n); progress(position()) }
        }
    }

    /** Declared size plus the slack the spec allows; no limit when the size is not declared. */
    fun limitFor(declaredSize: Long?): Long {
        val size = declaredSize?.coerceAtLeast(0) ?: return Long.MAX_VALUE
        val slack = Limits.OVERRUN_SLACK_BYTES
        return if (size > Long.MAX_VALUE - slack) Long.MAX_VALUE else size + slack
    }

    private fun fill(input: InputStream, buffer: ByteArray): Int {
        var n = 0
        while (n < buffer.size) {
            val r = input.read(buffer, n, buffer.size - n)
            if (r < 0) break
            if (r > 0) { n += r; continue }
            val one = input.read()          // a stream that answers 0: ask for one byte instead of spinning
            if (one < 0) break
            buffer[n++] = one.toByte()
        }
        return n
    }
}
