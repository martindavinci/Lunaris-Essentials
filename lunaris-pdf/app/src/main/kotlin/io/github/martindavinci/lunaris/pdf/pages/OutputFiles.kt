package io.github.martindavinci.lunaris.pdf.pages

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.actions.DocumentCopier
import io.github.martindavinci.lunaris.pdf.actions.attempt
import io.github.martindavinci.lunaris.pdf.recents.RecentVisit
import io.github.martindavinci.lunaris.pdf.recents.RecentsStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A finished file: [folder] only if its provider tells, as [DocumentPlaces] explains. */
data class OutputInfo(val name: String, val folder: String?, val bytes: Long?)

/** Where new files go. Every one of these copes with a provider that fails. */
interface OutputFiles {
    suspend fun save(from: String, to: String): Boolean
    suspend fun isEmpty(uri: String): Boolean
    suspend fun delete(uri: String)

    /** A new document in a folder the user chose, or null if it could not be made. */
    suspend fun createIn(folder: String, name: String): String?
    suspend fun describe(uri: String): OutputInfo?

    /** The new file joins the recents, and stays readable from there. */
    suspend fun remember(uri: String, name: String, pages: Int)
}

class ContentOutputFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copier: DocumentCopier,
    private val recents: RecentsStore,
) : OutputFiles {
    private val resolver get() = context.contentResolver

    override suspend fun save(from: String, to: String): Boolean = (copier as DocumentActions).saveCopy(from, to)

    override suspend fun isEmpty(uri: String): Boolean = withContext(Dispatchers.IO) {
        var empty = false
        attempt { resolver.openAssetFileDescriptor(uri.toUri(), READ)?.use { empty = it.length == 0L } }
        empty
    }

    override suspend fun delete(uri: String) {
        withContext(Dispatchers.IO) { attempt { DocumentsContract.deleteDocument(resolver, uri.toUri()) } }
    }

    override suspend fun createIn(folder: String, name: String): String? = withContext(Dispatchers.IO) {
        var made: Uri? = null
        attempt {
            val tree = folder.toUri()
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            made = DocumentsContract.createDocument(resolver, parent, PDF_MIME, name)
        }
        made?.toString()
    }

    override suspend fun describe(uri: String): OutputInfo? = withContext(Dispatchers.IO) {
        var info: OutputInfo? = null
        attempt {
            val parsed = uri.toUri()
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val documentId = if (DocumentsContract.isDocumentUri(context, parsed)) DocumentsContract.getDocumentId(parsed) else null
                    info = OutputInfo(
                        name = cursor.getString(0).orEmpty(),
                        folder = documentId?.let(DocumentPlaces::folderOf),
                        bytes = if (cursor.isNull(1)) null else cursor.getLong(1),
                    )
                }
            }
        }
        info
    }

    override suspend fun remember(uri: String, name: String, pages: Int) {
        withContext(Dispatchers.IO) {
            attempt { resolver.takePersistableUriPermission(uri.toUri(), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        recents.touch(RecentVisit(uri, name, pages))
    }

    private companion object {
        const val READ = "r"
        const val PDF_MIME = "application/pdf"
    }
}

/** Only the phone's own storage writes a path in its document ids: `primary:Download/moon.pdf`. Others hold nothing readable. */
object DocumentPlaces {
    private val STORAGE = Regex("^[A-Za-z0-9-]+:(.+)/[^/]+$")

    fun folderOf(documentId: String): String? = STORAGE.find(documentId)?.groupValues?.get(1)?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}
