package tools.lunaris.texteditor.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SafFileGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileGateway {

    override suspend fun readBytes(ref: FileRef): ByteArray = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.uri)
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("Cannot open input stream for $uri")
    }

    override suspend fun writeBytes(ref: FileRef, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.uri)
        // "wt" truncates existing content before writing.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw java.io.IOException("Cannot open output stream for $uri")
    }

    override suspend fun displayName(ref: FileRef): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.uri)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }
}
