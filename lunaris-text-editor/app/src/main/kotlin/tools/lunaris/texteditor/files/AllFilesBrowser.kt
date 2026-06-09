package tools.lunaris.texteditor.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AllFilesBrowser @Inject constructor() : FolderBrowser {

    override suspend fun list(locationRef: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val dir = File(locationRef)
        val children = dir.listFiles()?.toList() ?: emptyList()
        FileSorter.sort(
            children.map { f ->
                FileEntry(
                    name = f.name,
                    ref = f.absolutePath,
                    isDirectory = f.isDirectory,
                    extension = f.name.substringAfterLast('.', ""),
                )
            }
        )
    }

    override suspend fun parent(locationRef: String): String? =
        File(locationRef).parentFile?.absolutePath
}
