package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason

data class PlannedFile(val entry: ArchiveEntry, val folder: List<String>, val name: String)

data class SkippedEntry(val displayPath: String, val reason: SkipReason)

/**
 * What an extraction writes: folders (parents first), files with writable names, and the entries
 * set aside with the reason. A selection of folder rows is expanded by the caller.
 */
data class ExtractionPlan(
    val folders: List<List<String>>, val files: List<PlannedFile>, val skipped: List<SkippedEntry>, val declaredBytes: Long,
) {
    companion object {
        fun of(selection: List<ArchiveEntry>): ExtractionPlan {
            val folders = LinkedHashSet<List<String>>()
            val files = ArrayList<PlannedFile>()
            val skipped = ArrayList<SkippedEntry>()
            for (entry in selection) {
                val path = entry.path
                if (path !is SafePath.Result.Ok) {
                    skipped += SkippedEntry(entry.displayPath, (path as SafePath.Result.Skipped).reason)
                    continue
                }
                val names = path.components.map { SafeComponent.forWriting(it) }
                if (names.any { it == null }) {
                    skipped += SkippedEntry(entry.displayPath, SkipReason.INVALID_NAME)
                    continue
                }
                val written = names.filterNotNull()
                val folder = if (entry.type == EntryType.DIRECTORY) written else written.dropLast(1)
                for (depth in 1..folder.size) folders += folder.take(depth)
                if (entry.type != EntryType.DIRECTORY) files += PlannedFile(entry, folder, written.last())
            }
            return ExtractionPlan(folders.toList(), files, skipped, files.sumOf { it.entry.size ?: 0 })
        }
    }
}
