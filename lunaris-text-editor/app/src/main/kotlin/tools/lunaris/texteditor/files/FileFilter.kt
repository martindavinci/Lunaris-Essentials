package tools.lunaris.texteditor.files

object FileFilter {
    fun apply(entries: List<FileEntry>, knownExtensions: Set<String>, enabled: Boolean): List<FileEntry> {
        if (!enabled) return entries
        val known = knownExtensions.map { it.lowercase() }.toSet()
        return entries.filter { it.isDirectory || it.extension.lowercase() in known }
    }
}
