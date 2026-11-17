package io.github.martindavinci.lunaris.archives.edits

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

/** [SaveIo] over the ContentResolver: "Save as" documents and folders picked with the system picker. */
class ContentSaveIo @Inject constructor(private val resolver: ContentResolver) : SaveIo {
    override fun write(target: String, from: File) {
        val out = resolver.openOutputStream(Uri.parse(target), TRUNCATE) ?: throw FileNotFoundException(target)
        out.use { stream -> from.inputStream().use { it.copyTo(stream, Limits.WIRE_CHUNK_BYTES) } }
    }

    override fun createIn(tree: String, name: String, mime: String): String {
        val treeUri = Uri.parse(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return DocumentsContract.createDocument(resolver, parent, mime, name)?.toString() ?: throw FileNotFoundException(name)
    }

    private companion object { const val TRUNCATE = "wt" }
}
