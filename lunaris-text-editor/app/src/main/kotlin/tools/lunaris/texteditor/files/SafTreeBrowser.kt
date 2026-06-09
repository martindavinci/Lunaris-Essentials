package tools.lunaris.texteditor.files

import android.content.Context
import android.net.Uri
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
        // A tree root uri resolves via fromTreeUri; a child dir uri also works through it.
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
}
