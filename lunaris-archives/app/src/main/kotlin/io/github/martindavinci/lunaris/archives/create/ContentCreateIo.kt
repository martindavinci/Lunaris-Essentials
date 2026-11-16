package io.github.martindavinci.lunaris.archives.create

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.martindavinci.lunaris.archives.files.LocationLabel
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** [CreateIo] over the ContentResolver: picked sources in, the "Save as" document out. */
class ContentCreateIo @Inject constructor(private val resolver: ContentResolver) : CreateIo {
    override fun openInput(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)

    override fun openOutput(uri: String): OutputStream =
        resolver.openOutputStream(Uri.parse(uri), TRUNCATE) ?: throw FileNotFoundException(uri)

    override fun delete(uri: String) {
        runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(uri)) }
    }

    override fun size(uri: String): Long? = runCatching {
        resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    /** "Download" for a document id like "primary:Download/Holiday.zip": the folder it is in. */
    override fun location(uri: String): String? = runCatching {
        LocationLabel.of(DocumentsContract.getDocumentId(Uri.parse(uri)).substringBeforeLast('/', ""))
    }.getOrNull()

    private companion object { const val TRUNCATE = "wt" }
}
