package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.browse.EntryMime
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.FreeBytes
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import java.io.File
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
        val route = routeFor(archive, entry) ?: return CopyResult.Rar4Password
        if (entry.encrypted && password == null) return CopyResult.NeedsPassword
        copies.existingCopy(archive.source.key, entry)?.let { return ready(it) }
        if (freeBytes.get() < (entry.size ?: 0) + Limits.CACHE_MARGIN_BYTES) return CopyResult.NoSpace
        val target = copies.target(archive.source.key, entry)
        val result = try {
            write(archive, entry, route, password, target, onProgress)
        } catch (e: Throwable) {
            target.parentFile?.deleteRecursively()
            if (e is EngineStoppedException || e is OverrunException) return CopyResult.Damaged
            throw e
        }
        if (result != null) {
            target.parentFile?.deleteRecursively()
            return result
        }
        copies.trim(target, System.currentTimeMillis())
        return ready(target)
    }

    /** Spec "Instradamento" 2: encrypted entries go to the reader that can decrypt them; null for a protected RAR4. */
    private fun routeFor(archive: OpenArchive, entry: ArchiveEntry): EngineRoute? {
        if (!entry.encrypted) return archive.route
        return when (archive.index.kind) {
            ArchiveKind.ZIP -> EngineRoute.ZIP4J
            ArchiveKind.SEVEN_Z -> EngineRoute.SEVEN_Z
            ArchiveKind.RAR5 -> EngineRoute.LIB_RAR
            ArchiveKind.RAR4 -> null
            else -> archive.route
        }
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
