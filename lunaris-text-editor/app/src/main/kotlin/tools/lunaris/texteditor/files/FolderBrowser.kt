package tools.lunaris.texteditor.files

interface FolderBrowser {
    /** Lists the directory identified by [locationRef]; returns sorted entries (dirs first). */
    suspend fun list(locationRef: String): List<FileEntry>
    /** Parent ref of [locationRef], or null at the root. */
    suspend fun parent(locationRef: String): String?
}
