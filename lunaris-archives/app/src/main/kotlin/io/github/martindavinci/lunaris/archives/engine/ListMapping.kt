package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import kotlinx.coroutines.CancellationException

/** The status a listing ended with, plus what was read, as one answer for the browser. */
object ListMapping {
    fun toResult(status: EngineStatus, kind: ArchiveKind, route: EngineRoute, index: ArchiveIndex?): ListResult = when (status) {
        EngineStatus.OK, EngineStatus.LIMIT_REACHED -> index?.let { ListResult.Listed(it) } ?: damaged()
        EngineStatus.HEADERS_ENCRYPTED -> passwordFor(kind)
        EngineStatus.DATA_ENCRYPTED -> index?.let { ListResult.Listed(it) } ?: passwordFor(kind)
        EngineStatus.WRONG_PASSWORD -> ListResult.WrongPassword
        EngineStatus.RAR4_ENCRYPTED -> ListResult.Problem(ArchiveProblem.RAR4_PASSWORD)
        EngineStatus.DICTIONARY_TOO_LARGE -> ListResult.Reroute(EngineRoute.LIB_RAR)
        EngineStatus.NEEDS_NEXT_VOLUME -> ListResult.Problem(ArchiveProblem.SPLIT_ARCHIVE)
        EngineStatus.NOT_ARCHIVE -> ListResult.Problem(ArchiveProblem.NOT_ARCHIVE)
        EngineStatus.TRUNCATED, EngineStatus.DAMAGED ->
            index?.takeIf { it.entries.isNotEmpty() }?.let { ListResult.Listed(it.copy(partial = true)) } ?: damaged()
        EngineStatus.UNSUPPORTED_FEATURE -> ListResult.Problem(ArchiveProblem.UNSUPPORTED_FEATURE)
        EngineStatus.OUT_OF_MEMORY -> ListResult.Problem(ArchiveProblem.READER_STOPPED)
        EngineStatus.ACCESS_DENIED -> throw EngineAccessDeniedException()
        EngineStatus.CANCELLED -> throw CancellationException("cancelled on $route")
        EngineStatus.PATH_MISMATCH -> damaged()
    }

    private fun passwordFor(kind: ArchiveKind): ListResult = when (kind) {
        ArchiveKind.RAR5 -> ListResult.NeedsPassword(EngineRoute.LIB_RAR)
        ArchiveKind.SEVEN_Z -> ListResult.NeedsPassword(EngineRoute.SEVEN_Z)
        else -> ListResult.Problem(ArchiveProblem.UNSUPPORTED_FEATURE)
    }

    private fun damaged() = ListResult.Problem(ArchiveProblem.DAMAGED)
}
