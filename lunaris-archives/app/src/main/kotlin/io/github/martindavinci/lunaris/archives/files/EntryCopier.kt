package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.browse.EntryMime
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EntryRoutes
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.extract.FreeSpace
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.FreeBytes
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface EntryOpener {
    suspend fun copy(archive: OpenArchive, entry: ArchiveEntry, password: CharArray?, onProgress: (Long) -> Unit): CopyResult
}

sealed interface CopyResult {
    data class Ready(val uri: String, val mime: String, val path: String) : CopyResult
    data object WrongPassword : CopyResult
    data object NeedsPassword : CopyResult
    data object Damaged : CopyResult
    data object NoSpace : CopyResult
    data object Cancelled : CopyResult
    data object Changed : CopyResult
    data object Unsupported : CopyResult
    data object Rar4Password : CopyResult
    /** The archive itself can no longer be read: moved, deleted, or its permission given back. */
    data object Gone : CopyResult
}

/** The content: URI other apps get for a file of ours (FileProvider on the device). */
fun interface FileUris {
    fun uriFor(file: File): String
}

/** The platform's MIME type for an extension (MimeTypeMap on the device). */
fun interface PlatformMime {
    fun of(extension: String): String?
}

/** One entry copied into cache/open for another app, through the engine of its route. */
class EntryCopier @Inject constructor(
    private val engine: ArchiveEngine,
    private val copies: TempCopies,
    private val freeBytes: FreeBytes,
    private val uris: FileUris,
    private val platformMime: PlatformMime,
) : EntryOpener {
    override suspend fun copy(archive: OpenArchive, entry: ArchiveEntry, password: CharArray?, onProgress: (Long) -> Unit): CopyResult {
        val route = EntryRoutes.routeFor(archive, entry) ?: return CopyResult.Rar4Password
        if (entry.encrypted && password == null) return CopyResult.NeedsPassword
        // Everything below touches the disk; the free-space query alone can take seconds.
        return withContext(Dispatchers.IO) {
            try {
                copyToCache(archive, entry, route, password, onProgress)
            } catch (_: IOException) {
                CopyResult.Gone   // our own copy went away under us (the cache was cleared)
            }
        }
    }

    private suspend fun copyToCache(
        archive: OpenArchive, entry: ArchiveEntry, route: EngineRoute, password: CharArray?, onProgress: (Long) -> Unit,
    ): CopyResult {
        val key = archive.source.key
        copies.existingCopy(key, entry)?.let { return ready(it) }
        if (freeBytes.get() < (entry.size ?: 0) + Limits.CACHE_MARGIN_BYTES) return CopyResult.NoSpace
        val target = copies.target(key, entry)
        // Written in a folder of its own and moved into place when whole: a copy that is cancelled or
        // fails removes only what it wrote, never the file a second tap on the same entry is writing.
        val attempt = copies.attempt(target)
        val result = try {
            write(archive, entry, route, password, File(attempt, target.name), onProgress)
        } catch (e: Throwable) {
            attempt.deleteRecursively()
            return when (e) {
                is EngineStoppedException, is OverrunException -> CopyResult.Damaged
                is CancellationException -> throw e
                is IOException -> if (FreeSpace.isNoSpace(e)) CopyResult.NoSpace else CopyResult.Gone
                is SecurityException -> CopyResult.Gone
                else -> throw e
            }
        }
        if (result != null) {
            attempt.deleteRecursively()
            return result
        }
        val file = copies.publish(attempt, target, entry) ?: return CopyResult.Damaged
        copies.trim(file, System.currentTimeMillis())
        return ready(file)
    }

    /** Null when the entry was written whole; else why not. */
    private suspend fun write(
        archive: OpenArchive, entry: ArchiveEntry, route: EngineRoute, password: CharArray?, target: File, onProgress: (Long) -> Unit,
    ): CopyResult? = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val limit = (entry.size ?: Limits.MAX_TEMP_COPY_BYTES) + Limits.OVERRUN_SLACK_BYTES
        var written = 0L
        var outcome: EntryOutcome? = null
        var status: EngineStatus? = null
        target.outputStream().buffered().use { out ->
            engine.extract(archive.source, route, password, longArrayOf(entry.index), mapOf(entry.index to entry.rawPath)).collect { event ->
                when (event) {
                    is EngineEvent.Bytes -> {
                        written += event.length
                        if (written > limit) throw OverrunException()
                        out.write(event.chunk, 0, event.length)
                        onProgress(written)
                    }
                    is EngineEvent.EntryFinished -> outcome = event.outcome
                    is EngineEvent.Finished -> status = event.status
                    is EngineEvent.EntryStarted, is EngineEvent.Progress -> Unit
                }
            }
        }
        verdict(outcome, status)
    }

    private fun verdict(outcome: EntryOutcome?, status: EngineStatus?): CopyResult? = when {
        status == EngineStatus.PATH_MISMATCH -> CopyResult.Changed
        outcome == EntryOutcome.OK && status == EngineStatus.OK -> null
        outcome == EntryOutcome.WRONG_PASSWORD || status == EngineStatus.WRONG_PASSWORD -> CopyResult.WrongPassword
        outcome == EntryOutcome.ENCRYPTED -> CopyResult.NeedsPassword
        outcome == EntryOutcome.UNSUPPORTED || status == EngineStatus.UNSUPPORTED_FEATURE -> CopyResult.Unsupported
        outcome == EntryOutcome.CANCELLED || status == EngineStatus.CANCELLED -> CopyResult.Cancelled
        else -> CopyResult.Damaged
    }

    private fun ready(file: File): CopyResult.Ready {
        val head = file.inputStream().use { input -> ByteArray(Limits.TEXT_SNIFF_BYTES).let { it.copyOf(maxOf(0, input.read(it))) } }
        return CopyResult.Ready(uris.uriFor(file), EntryMime.of(file.name, head, platformMime::of), file.path)
    }

    private class OverrunException : RuntimeException("entry larger than declared")
}
