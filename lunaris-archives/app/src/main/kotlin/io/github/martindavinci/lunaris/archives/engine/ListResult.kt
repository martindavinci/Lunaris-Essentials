package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex

/** What listing an archive gave. */
sealed interface ListResult {
    data class Listed(val index: ArchiveIndex) : ListResult
    data class NeedsPassword(val route: EngineRoute) : ListResult
    data object WrongPassword : ListResult
    data class Problem(val problem: ArchiveProblem) : ListResult
    /** Try again on another reader, e.g. a RAR5 dictionary too large for libarchive goes to lib-rar. */
    data class Reroute(val route: EngineRoute) : ListResult
}

/** Why an archive cannot be shown; each has its own screen. */
enum class ArchiveProblem { NOT_ARCHIVE, DAMAGED, RAR4_PASSWORD, UNSUPPORTED_FEATURE, SPLIT_ARCHIVE, READER_STOPPED, GONE }

/** The engine could not read the file it was given: the grant was lost. */
class EngineAccessDeniedException : java.io.IOException("access denied")
