package tools.lunaris.texteditor.feature.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val SHARE_DIR = "share"

/**
 * Shares [text] as a file named [fileName]. Writes a private copy into the app's cache and hands
 * out a one-shot read grant via FileProvider — no storage permissions, Play-policy clean. The
 * recipient gets a real file with the document's name/extension instead of loose text.
 */
fun shareDocumentAsFile(context: Context, fileName: String, text: String) {
    val dir = prepareShareDir(context.cacheDir)
    val file = File(dir, sanitizeFileName(fileName))
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Empties (and creates) the share folder under [cacheDir] and returns it. Only the latest shared
 * copy is kept, so document text doesn't pile up in cache; also run at app start.
 */
fun prepareShareDir(cacheDir: File): File {
    val dir = File(cacheDir, SHARE_DIR)
    dir.listFiles()?.forEach { it.delete() }
    return dir.apply { mkdirs() }
}

/** Strips path separators and characters illegal in file names so the copy always writes. */
internal fun sanitizeFileName(name: String): String {
    val cleaned = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
    // "", "." and ".." aren't writable file names.
    return if (cleaned.all { it == '.' }) UNTITLED_SHARE_NAME else cleaned
}

private const val UNTITLED_SHARE_NAME = "untitled.txt"
