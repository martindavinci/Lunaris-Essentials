package tools.lunaris.texteditor.files

object FileSorter {
    fun sort(entries: List<FileEntry>): List<FileEntry> =
        entries.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
}
