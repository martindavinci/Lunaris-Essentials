package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.RawPath
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Called from C by name (R8 keep rule; signatures are repeated in native/jni/engine_sink.c).
 *
 * - no method lets an exception out for an ordinary reason: a closed pipe is remembered in
 *   [closed] and answered with STOP, so C never makes a JNI call with an exception pending;
 * - [onData] and [onEntryStart] answer with a code, so C can tell "this entry is too big"
 *   (go on with the next) from "nobody is reading any more" (stop the job);
 * - entries of every format but ZIP are written as they arrive, so the main process can count
 *   them while a big tar.gz is read; ZIP entries wait in [buffered] for the central directory;
 * - the engine's position is reported at the start of every entry (see RatioGuard);
 * - the error text arrives as bytes: it can quote a name that is not valid UTF-8.
 */
class NativeSink(private val out: WireWriter, private val expected: Map<Long, RawPath>?) {
    @JvmField val buffer: ByteBuffer = ByteBuffer.allocateDirect(EngineWire.CHUNK_BYTES)
    private val chunk = ByteArray(EngineWire.CHUNK_BYTES)

    /** ZIP entries, kept until LibarchiveReader has merged the central directory into them. */
    val buffered = ArrayList<WireEntry>()
    var format = 0; private set
    var filters = IntArray(0); private set
    var encrypted = 0; private set
    var lastErrno = 0; private set
    var lastMessage: String? = null; private set
    var closed = false; private set

    private var passThrough: Boolean? = null
    private var entryLimit = Long.MAX_VALUE
    private var entryBytes = 0L

    /** Twice per listing: after the first header (format known) and at the end (encryption known). */
    fun onArchive(format: Int, filters: IntArray, encrypted: Int) {
        this.format = format
        this.filters = filters
        this.encrypted = encrypted
        if (passThrough == null) passThrough = LibarchiveCodes.kind(format, filters).first != ArchiveKind.ZIP
    }

    fun onEntry(
        index: Long, path: ByteArray, utf8: Boolean, type: Int, size: Long, hasSize: Boolean,
        mtimeSeconds: Long, hasMtime: Boolean, encrypted: Boolean, link: ByteArray?, hardlink: Boolean,
    ): Boolean {
        val wire = WireEntry(
            index, RawPath.owning(path), utf8, LibarchiveCodes.entryType(type, hardlink), size.takeIf { hasSize },
            null, (mtimeSeconds * MILLIS_PER_SECOND).takeIf { hasMtime }, encrypted, null, link?.let(RawPath::owning), null,
        )
        if (passThrough != true) {
            buffered += wire
            return true
        }
        return write { entry(wire) }
    }

    fun onEntryStart(index: Long, path: ByteArray, size: Long, hasSize: Boolean, archiveBytes: Long): Int {
        val raw = RawPath.owning(path)
        if (expected != null && expected[index] != raw) return MISMATCH
        entryLimit = EntryPump.limitFor(size.takeIf { hasSize })
        entryBytes = 0
        lastErrno = 0
        lastMessage = null
        return if (write { dataStart(index, raw); progress(archiveBytes) }) CONTINUE else STOP
    }

    fun onData(length: Int, archiveBytes: Long): Int {
        if (length < 0 || length > chunk.size) return STOP
        entryBytes += length
        if (entryBytes > entryLimit) return LIMIT
        buffer.clear()
        buffer.get(chunk, 0, length)
        return if (write { data(chunk, 0, length); progress(archiveBytes) }) CONTINUE else STOP
    }

    fun onEntryEnd(index: Long, outcome: Int): Boolean {
        val reported = EntryOutcome.of(outcome)
        val unreadable = reported == EntryOutcome.DAMAGED &&
            EngineErrors.classify(ArchiveKind.UNKNOWN, lastErrno, lastMessage) == EngineStatus.DATA_ENCRYPTED
        return write { dataEnd(index, if (unreadable) EntryOutcome.ENCRYPTED else reported) }
    }

    /** While an extraction skips towards the entry it wants: flushed, so the main process sees it. */
    fun onProgress(archiveBytes: Long): Boolean = write { progress(archiveBytes); flush() }

    fun onError(archiveErrno: Int, message: ByteArray?) {
        lastErrno = archiveErrno
        lastMessage = message?.toString(Charsets.UTF_8)
    }

    private inline fun write(block: WireWriter.() -> Unit): Boolean {
        if (closed) return false
        return try {
            out.block()
            true
        } catch (_: IOException) {
            closed = true
            false
        }
    }

    companion object {
        // Mirrored as SINK_* in native/jni/engine_status.h (EngineStatusHeaderTest compares).
        const val CONTINUE = 0
        const val LIMIT = 1
        const val MISMATCH = 2
        const val STOP = 3
        private const val MILLIS_PER_SECOND = 1000L
    }
}
