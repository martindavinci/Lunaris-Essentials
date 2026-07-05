package tools.lunaris.texteditor.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SafTreeBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
) : FolderBrowser {

    private fun docFor(ref: String): DocumentFile? {
        val uri = Uri.parse(ref)
        // A tree root uri resolves via fromTreeUri; a child dir uri (tree+document) also
        // works. Reject non-tree (single-document) uris — fromTreeUri throws on them.
        if (!DocumentsContract.isTreeUri(uri)) return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    override suspend fun list(locationRef: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val doc = docFor(locationRef) ?: return@withContext emptyList()
        FileSorter.sort(
            doc.listFiles().mapNotNull { child ->
                val name = child.name ?: return@mapNotNull null
                FileEntry(
                    name = name,
                    ref = child.uri.toString(),
                    isDirectory = child.isDirectory,
                    extension = name.substringAfterLast('.', ""),
                )
            }
        )
    }

    override suspend fun parent(locationRef: String): String? = withContext(Dispatchers.IO) {
        docFor(locationRef)?.parentFile?.uri?.toString()
    }

    /** Create a text file named [name] under [parentRef]; returns its uri or null. */
    suspend fun createFile(parentRef: String, name: String): String? = withContext(Dispatchers.IO) {
        runCatching { docFor(parentRef)?.createFile("text/plain", name)?.uri?.toString() }.getOrNull()
    }

    /** Create a subfolder named [name] under [parentRef]; returns its uri or null. */
    suspend fun createFolder(parentRef: String, name: String): String? = withContext(Dispatchers.IO) {
        runCatching { docFor(parentRef)?.createDirectory(name)?.uri?.toString() }.getOrNull()
    }

    /** Rename the document/folder [ref] to [newName]; returns its (possibly new) uri or null. */
    suspend fun rename(ref: String, newName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = docFor(ref) ?: return@runCatching null
            if (doc.renameTo(newName)) doc.uri.toString() else null
        }.getOrNull()
    }
}
