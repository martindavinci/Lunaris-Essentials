package io.github.martindavinci.lunaris.archives.recents

/** One row of the recents; [lastPath] is null for archives whose names are encrypted. */
data class RecentArchive(
    val uri: String, val name: String, val formatLabel: String, val sizeBytes: Long?, val fileCount: Int,
    val locked: Boolean, val lastOpenedMillis: Long, val lastPath: List<String>?,
)
