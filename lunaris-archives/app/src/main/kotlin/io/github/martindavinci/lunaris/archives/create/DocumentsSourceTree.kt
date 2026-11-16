package io.github.martindavinci.lunaris.archives.create

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import javax.inject.Inject

/** [SourceTree] over DocumentsContract: one query per folder, no DocumentFile. */
class DocumentsSourceTree @Inject constructor(private val resolver: ContentResolver) : SourceTree {
    override fun describe(uri: String): Described? = runCatching {
        resolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val flags = c.long(Document.COLUMN_FLAGS) ?: 0
            Described(
                name = c.string(OpenableColumns.DISPLAY_NAME) ?: Uri.parse(uri).lastPathSegment.orEmpty(),
                sizeBytes = c.long(OpenableColumns.SIZE),
                modifiedMillis = c.long(Document.COLUMN_LAST_MODIFIED),
                isVirtual = flags and Document.FLAG_VIRTUAL_DOCUMENT.toLong() != 0L,
            )
        }
    }.getOrNull()

    override fun rootOf(treeUri: String): Pair<String, String>? = runCatching {
        val tree = Uri.parse(treeUri)
        val id = DocumentsContract.getTreeDocumentId(tree)
        val name = resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        name?.let { id to it }
    }.getOrNull()

    override fun children(treeUri: String, documentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), documentId)
        return runCatching {
            resolver.query(uri, COLUMNS, null, null, null)?.use { c ->
                generateSequence { if (c.moveToNext()) c else null }.map { row ->
                    val flags = row.long(Document.COLUMN_FLAGS) ?: 0
                    Child(
                        documentId = row.string(Document.COLUMN_DOCUMENT_ID).orEmpty(),
                        name = row.string(Document.COLUMN_DISPLAY_NAME).orEmpty(),
                        isFolder = row.string(Document.COLUMN_MIME_TYPE) == Document.MIME_TYPE_DIR,
                        sizeBytes = row.long(Document.COLUMN_SIZE),
                        modifiedMillis = row.long(Document.COLUMN_LAST_MODIFIED),
                        isVirtual = flags and Document.FLAG_VIRTUAL_DOCUMENT.toLong() != 0L,
                    )
                }.toList()
            }
        }.getOrNull().orEmpty()
    }

    override fun uriOf(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()

    private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun Cursor.long(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private companion object {
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )
    }
}
