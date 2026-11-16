package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.RatioGuard
import java.io.IOException
import java.io.OutputStream

/**
 * The part of an extraction that writes: it takes the engine's events one by one and turns them
 * into files of the destination. This loop is where the limits of the spec are enforced on the
 * main side, whatever the engine sends:
 * - bytes only for an entry that was planned and has been started;
 * - no more than the declared size plus the slack per entry, and [totalLimit] for the run;
 * - the ratio guard asks before the chunk that crossed the line is written.
 * I/O errors of the destination are thrown to the runner, which knows ENOSPC from "folder gone".
 */
internal class ExtractionWriter(
    private val target: SafTarget,
    private val files: Map<Long, ResolvedFile>,          // by entry index
    private val guard: RatioGuard,
    private val totalLimit: Long,
    private val marker: ExtractMarker,
    private val askLarge: suspend (name: String) -> Boolean,
    private val onProgress: (written: Int, bytes: Long, current: String) -> Unit,
) {
    /** [existing]: the user's file this one replaces; it is touched only after [created] is complete. */
    private class Open(val file: ResolvedFile, val created: Created, val existing: Created?, val stream: OutputStream, val limit: Long) {
        var bytes = 0L
        var dropped = false
        val name: String get() = file.file.entry.displayPath
    }

    /** Entries that were not written: damaged, too big, refused, or without the right password. */
    val damaged = ArrayList<String>()
    var needPassword = 0; private set
    var written = 0; private set
    var bytes = 0L; private set

    /** The run wrote more than the archive declared in all: the runner stops collecting. */
    var overTotal = false; private set

    private val folders = HashMap<List<String>, String>()
    private var open: Open? = null

    /** Null until the engine says it is done; then its status. */
    suspend fun onEvent(event: EngineEvent): EngineStatus? {
        when (event) {
            is EngineEvent.EntryStarted -> start(event.index)
            is EngineEvent.Progress -> guard.onArchiveBytes(event.archiveBytesRead)
            is EngineEvent.Bytes -> write(event.chunk, event.length)
            is EngineEvent.EntryFinished -> finish(event.outcome)
            is EngineEvent.Finished -> return event.status
        }
        return null
    }

    /** Stop or failure: the file being written is removed, finished ones stay. */
    fun abandon() {
        val entry = open ?: return
        open = null
        runCatching { entry.stream.close() }
        if (!entry.dropped) target.delete(entry.created)
    }

    private fun start(index: Long) {
        val file = files[index] ?: throw EngineStoppedException()   // never asked for: not written
        abandon()
        val folder = folders.getOrPut(file.file.folder) { target.ensureFolder(file.file.folder) }
        // A replaced file is written beside the old one, which stays until the new one is complete:
        // a wrong password, a damaged entry or Stop never costs the user the file they had.
        val existing = if (file.replace) target.replace(folder, file.targetName) else null
        val created = target.createFile(folder, file.targetName)
        marker.set(created.token)
        val stream = target.open(created)
        guard.onEntryStart()
        open = Open(file, created, existing, stream, limitFor(file.file.entry.size))
    }

    private suspend fun write(chunk: ByteArray, length: Int) {
        val entry = open ?: throw EngineStoppedException()
        if (entry.dropped) return
        entry.bytes += length
        bytes += length
        if (bytes > totalLimit) overTotal = true
        if (overTotal || entry.bytes > entry.limit) return drop(entry)
        if (guard.onOutput(length.toLong()) == RatioGuard.Verdict.ASK) {
            // Nothing is read from the engine while the user decides: the pipe fills and the engine waits.
            if (askLarge(entry.name)) guard.allowCurrentEntry() else return drop(entry)
        }
        entry.stream.write(chunk, 0, length)
        onProgress(written, bytes, entry.name)
    }

    private fun finish(outcome: EntryOutcome) {
        val entry = open ?: return
        open = null
        if (entry.dropped) return
        if (outcome == EntryOutcome.OK) {
            try {
                entry.stream.close()   // may throw: a full disk often shows only here
            } catch (e: IOException) {
                target.delete(entry.created)
                throw e
            }
            entry.existing?.let { old ->
                target.delete(old)
                target.rename(entry.created, old.name)   // when the provider cannot, the new file keeps its name
            }
            written++
            return
        }
        runCatching { entry.stream.close() }
        target.delete(entry.created)
        damaged += entry.name
        if (outcome == EntryOutcome.WRONG_PASSWORD || outcome == EntryOutcome.ENCRYPTED) needPassword++
    }

    /** The rest of this entry is thrown away; what was written of it is removed. */
    private fun drop(entry: Open) {
        entry.dropped = true
        runCatching { entry.stream.close() }
        target.delete(entry.created)
        damaged += entry.name
    }

    private fun limitFor(declared: Long?): Long {
        val size = declared?.coerceAtLeast(0) ?: return Long.MAX_VALUE
        return if (size > Long.MAX_VALUE - Limits.OVERRUN_SLACK_BYTES) Long.MAX_VALUE else size + Limits.OVERRUN_SLACK_BYTES
    }
}
