package io.github.martindavinci.lunaris.archives.edits

/** The changed files of one archive, as the banner and the home card show them. */
data class ChangedArchive(val archiveUri: String, val archiveName: String, val updatable: Boolean, val entries: List<EditRecord>) {
    companion object {
        /** Records with a change, grouped by archive in the order they were handed out. */
        fun of(records: List<EditRecord>): List<ChangedArchive> =
            records.filter { it.pending != null }.groupBy { it.archiveUri }.map { (uri, list) ->
                ChangedArchive(uri, list.first().archiveName, list.all { it.updatable }, list)
            }
    }
}
