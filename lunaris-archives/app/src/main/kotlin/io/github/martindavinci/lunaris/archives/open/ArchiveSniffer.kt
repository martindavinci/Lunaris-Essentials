package io.github.martindavinci.lunaris.archives.open

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.ArchiveSignatures
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether one shared file is an archive (then it opens) or anything else (then it goes into a new ZIP). */
class ArchiveSniffer @Inject constructor(private val resolver: ContentResolver) {
    suspend fun looksLikeArchive(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            val head = resolver.openInputStream(parsed)?.use { input -> readHead(input) } ?: return@runCatching false
            ArchiveSignatures.detect(head, displayName(parsed)) != ArchiveKind.UNKNOWN
        }.getOrDefault(false)
    }

    private fun readHead(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(ArchiveSignatures.HEAD_BYTES)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
