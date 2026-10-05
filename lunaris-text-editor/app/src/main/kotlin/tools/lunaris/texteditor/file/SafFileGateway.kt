package tools.lunaris.texteditor.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class SafFileGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileGateway {

    override suspend fun readBytes(ref: FileRef, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { return@withContext it.inputStream().use { s -> s.readBounded(maxBytes) } }
        val uri = Uri.parse(ref.uri)
        context.contentResolver.openInputStream(uri)?.use { it.readBounded(maxBytes) }
            ?: throw java.io.IOException("Cannot open input stream for $uri")
    }

    override suspend fun writeBytes(ref: FileRef, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = fileOrNull(ref)
        if (file != null) {
            // Local file: write to a temp sibling then rename over the target, so a failed write
            // can't leave the original truncated. Falls back to a direct write if rename can't run.
            val tmp = file.parentFile?.let { File(it, file.name + ".lt-tmp") }
            if (tmp != null && runCatching { tmp.writeBytes(bytes); tmp.renameTo(file) }.getOrDefault(false)) {
                return@withContext
            }
            tmp?.delete()
            file.writeBytes(bytes)
            return@withContext
        }
        // content:// (incl. cloud providers like Drive/OneDrive): "wt" truncates then writes. SAF
        // gives no portable atomic-replace here, so write the full buffer in one shot and flush.
        val uri = Uri.parse(ref.uri)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
            ?: throw java.io.IOException("Cannot open output stream for $uri")
    }

    /** Reads up to [maxBytes]; throws [FileTooLargeException] if the stream is larger. */
    private fun InputStream.readBounded(maxBytes: Long): ByteArray {
        if (maxBytes >= Long.MAX_VALUE) return readBytes()
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(chunk)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw FileTooLargeException(maxBytes)
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    override suspend fun displayName(ref: FileRef): String? = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { return@withContext it.name }
        val uri = Uri.parse(ref.uri)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    override suspend fun size(ref: FileRef): Long? = withContext(Dispatchers.IO) {
        fileOrNull(ref)?.let { return@withContext it.length().takeIf { len -> len > 0 } }
        val uri = Uri.parse(ref.uri)
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    /**
     * Refs from the all-files browser are raw filesystem paths (or file:// URIs), not content
     * URIs — the [android.content.ContentResolver] can't open those. Returns a [File] for those,
     * null for content:// (and other) schemes, which go through the resolver.
     */
    private fun fileOrNull(ref: FileRef): File? {
        val uri = Uri.parse(ref.uri)
        val file = when (uri.scheme) {
            null -> File(ref.uri)            // bare path: /storage/emulated/0/foo.txt
            "file" -> uri.path?.let(::File)  // file:///storage/emulated/0/foo.txt
            else -> null                      // content://, etc.
        } ?: return null
        // The exported VIEW/EDIT filter accepts file:// from any app: never let one point us at
        // our own private storage (session text, settings), whatever `..`/symlinks it uses.
        if (isInsideAny(file, privateRoots)) throw java.io.FileNotFoundException("Not accessible: ${file.name}")
        return file
    }

    private val privateRoots: List<File> by lazy { listOf(File(context.applicationInfo.dataDir)) }
}
