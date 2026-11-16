package io.github.martindavinci.lunaris.archives.session

import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.engine.EngineRoute

/** An archive the browser shows; [parentId] and [nestedEntry] when it was opened from inside another. */
data class OpenArchive(
    val id: String, val source: ArchiveSource, val route: EngineRoute, val index: ArchiveIndex,
    val parentId: String?, val nestedEntry: Long?,
)

sealed interface OpenOutcome {
    data class Opened(val archive: OpenArchive) : OpenOutcome
    data class NeedsPassword(val pending: PendingOpen) : OpenOutcome
    data class WrongPassword(val pending: PendingOpen) : OpenOutcome
    data class NeedsCopy(val uri: String, val bytes: Long?) : OpenOutcome
    data class Problem(val problem: ArchiveProblem) : OpenOutcome
}

/** An opening that waits for a password, on the route that asked for it. */
data class PendingOpen(val source: ArchiveSource, val route: EngineRoute, val parentId: String?, val nestedEntry: Long?)

/** What the browser route was asked to open; its id is the route argument. */
data class OpenRequest(
    val uri: String, val parentId: String?, val nestedEntry: Long?, val nestedFile: String?,
    /** Continue: the folder to show first. */
    val startPath: List<String>? = null,
    /** "Extract all…" from Home: the browser asks for the folder as soon as the archive is open. */
    val extractAll: Boolean = false,
)
