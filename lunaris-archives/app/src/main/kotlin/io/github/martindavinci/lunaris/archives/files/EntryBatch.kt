package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import java.io.File
import java.io.OutputStream

/** One entry of a batch: its finished copy, or why there is none. */
internal sealed interface BatchCopy {
    data class Copied(val file: File) : BatchCopy
    data class Failed(val result: CopyResult) : BatchCopy
}

/**
 * Several entries of one route in one engine pass: a solid archive is read once, not once per entry.
 * Each entry is written in an attempt folder of its own and published when whole, like a single copy.
 */
internal class EntryBatch(private val engine: ArchiveEngine, private val copies: TempCopies) {
    /** Each entry's outcome by index. Throws what the engine or the disk threw. */
    suspend fun copy(archive: OpenArchive, route: EngineRoute, password: CharArray?, entries: List<ArchiveEntry>): Map<Long, BatchCopy> {
        val key = archive.source.key
        val byIndex = entries.associateBy { it.index }
        val targets = entries.associate { it.index to copies.target(key, it) }
        val attempts = targets.mapValues { (_, target) -> copies.attempt(target) }
        val outcomes = HashMap<Long, EntryOutcome>()
        var status: EngineStatus? = null
        var out: OutputStream? = null
        var current: ArchiveEntry? = null
        var written = 0L
        try {
            val indices = entries.map { it.index }.sorted().toLongArray()
            engine.extract(archive.source, route, password, indices, entries.associate { it.index to it.rawPath }).collect { event ->
                when (event) {
                    is EngineEvent.EntryStarted -> {
                        out?.close()
                        current = byIndex[event.index]
                        written = 0
                        out = current?.let { e -> File(attempts.getValue(e.index), targets.getValue(e.index).name).also { it.parentFile?.mkdirs() }.outputStream().buffered() }
                    }
                    is EngineEvent.Bytes -> {
                        val entry = current ?: return@collect
                        written += event.length
                        if (written > (entry.size ?: Limits.MAX_TEMP_COPY_BYTES) + Limits.OVERRUN_SLACK_BYTES) throw OverrunException()
                        out?.write(event.chunk, 0, event.length)
                    }
                    is EngineEvent.EntryFinished -> {
                        out?.close()
                        out = null
                        current = null
                        outcomes[event.index] = event.outcome
                    }
                    is EngineEvent.Finished -> status = event.status
                    is EngineEvent.Progress -> Unit
                }
            }
        } catch (e: Throwable) {
            runCatching { out?.close() }
            attempts.values.forEach { it.deleteRecursively() }
            throw e
        }
        return entries.associate { entry ->
            val attempt = attempts.getValue(entry.index)
            val why = copyVerdict(outcomes[entry.index], status)
            val done = if (why == null) {
                copies.publish(attempt, targets.getValue(entry.index), entry)?.let { BatchCopy.Copied(it) } ?: BatchCopy.Failed(CopyResult.Damaged)
            } else {
                attempt.deleteRecursively()
                BatchCopy.Failed(why)
            }
            entry.index to done
        }
    }
}
