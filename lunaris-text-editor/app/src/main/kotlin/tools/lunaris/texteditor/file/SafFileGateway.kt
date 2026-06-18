package tools.lunaris.texteditor.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class SafFileGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileGateway {

    override suspend fun readBytes(ref: FileRef): ByteArray = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { return@withContext it.readBytes() }
        val uri = Uri.parse(ref.uri)
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("Cannot open input stream for $uri")
    }

    override suspend fun writeBytes(ref: FileRef, bytes: ByteArray) = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { it.writeBytes(bytes); return@withContext }
        val uri = Uri.parse(ref.uri)
        // "wt" truncates existing content before writing.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw java.io.IOException("Cannot open output stream for $uri")
    }

    override suspend fun displayName(ref: FileRef): String? = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { return@withContext it.name }
        val uri = Uri.parse(ref.uri)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    /**
     * Refs from the all-files browser are raw filesystem paths (or file:// URIs), not content
     * URIs — the [android.content.ContentResolver] can't open those. Returns a [File] for those,
     * null for content:// (and other) schemes, which go through the resolver.
     */
    private fun fileOrNull(ref: FileRef): File? {
        val uri = Uri.parse(ref.uri)
        return when (uri.scheme) {
            null -> File(ref.uri)            // bare path: /storage/emulated/0/foo.txt
            "file" -> uri.path?.let(::File)  // file:///storage/emulated/0/foo.txt
            else -> null                      // content://, etc.
        }
    }
}
