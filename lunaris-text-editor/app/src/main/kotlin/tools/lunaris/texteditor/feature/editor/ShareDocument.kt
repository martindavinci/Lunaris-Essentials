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
    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
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

/** Strips path separators and characters illegal in file names so the copy always writes. */
private fun sanitizeFileName(name: String): String =
    name.trim().ifBlank { "untitled.txt" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
