package io.github.martindavinci.lunaris.archives.extract

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.martindavinci.lunaris.archives.files.LocationLabel
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.Locale

/**
 * [SafTarget] over a tree picked with OpenDocumentTree.
 * One query per folder, no DocumentFile, and every child keeps its kind: see [ExistingNames].
 */
class DocumentsTarget(private val resolver: ContentResolver, private val tree: Uri) : SafTarget {
    private class Child(val id: String, val name: String, val isFolder: Boolean)

    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)
    private val listings = HashMap<String, MutableMap<String, Child>>()   // folder id -> lower-cased name -> child
    private var firstCreated: Uri? = null

    override val label: String? get() = LocationLabel.of(rootId)

    override fun children(folder: List<String>): ExistingNames? {
        var id = rootId
        for (name in folder) id = list(id)[key(name)]?.takeIf { it.isFolder }?.id ?: return null
        val kids = list(id)
        return ExistingNames(
            files = kids.filterValues { !it.isFolder }.keys.toSet(),
            folders = kids.filterValues { it.isFolder }.keys.toSet(),
        )
    }

    override fun ensureFolder(folder: List<String>): String {
        var id = rootId
        for (name in folder) {
            val kids = list(id)
            // A file of the same name is not this folder: the provider then gives the new folder another name.
            id = kids[key(name)]?.takeIf { it.isFolder }?.id
                ?: create(id, Document.MIME_TYPE_DIR, name).also { kids[key(name)] = Child(it, name, isFolder = true) }
        }
        return id
    }

    override fun createFile(folderToken: String, name: String): Created {
        val id = create(folderToken, BINARY, name)
        // Recorded under the name that was asked for, unless that name is taken (then the provider chose
        // another one). Asking for the name it really used costs a query per file.
        list(folderToken).putIfAbsent(key(name), Child(id, name, isFolder = false))
        val uri = uriOf(id)
        if (firstCreated == null) firstCreated = uri
        return Created(uri.toString(), name)
    }

    override fun replace(folderToken: String, name: String): Created? {
        val child = list(folderToken)[key(name)]?.takeIf { !it.isFolder } ?: return null
        return Created(uriOf(child.id).toString(), child.name, existed = true)
    }

    override fun open(created: Created): OutputStream =
        guarded { resolver.openOutputStream(Uri.parse(created.token), WRITE) } ?: throw FolderGoneException()

    override fun delete(created: Created) {
        if (runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(created.token)) }.getOrDefault(false)) forget(created.token)
    }

    override fun rename(created: Created, name: String): Created? {
        val renamed = runCatching { DocumentsContract.renameDocument(resolver, Uri.parse(created.token), name) }.getOrNull() ?: return null
        val folder = forget(created.token)
        val id = DocumentsContract.getDocumentId(renamed)
        if (folder != null) listings[folder]?.set(key(name), Child(id, name, isFolder = false))
        return Created(renamed.toString(), name)
    }

    /** Drops a document from the listings; returns the folder it was in. */
    private fun forget(token: String): String? {
        val id = runCatching { DocumentsContract.getDocumentId(Uri.parse(token)) }.getOrNull() ?: return null
        for ((folder, kids) in listings) {
            val name = kids.entries.firstOrNull { it.value.id == id }?.key ?: continue
            kids.remove(name)
            return folder
        }
        return null
    }

    /** From the first file this run created, and only when it is a regular file (internal storage, SD card). */
    override fun freeBytes(): Long? {
        val uri = firstCreated ?: return null
        return try {
            resolver.openFileDescriptor(uri, WRITE)?.use { pfd ->
                if (!OsConstants.S_ISREG(Os.fstat(pfd.fileDescriptor).st_mode)) return null
                val stat = Os.fstatvfs(pfd.fileDescriptor)
                stat.f_bavail * stat.f_frsize
            }
        } catch (_: ErrnoException) {
            null
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private fun list(folderId: String): MutableMap<String, Child> = listings.getOrPut(folderId) {
        val kids = HashMap<String, Child>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = guarded { resolver.query(uri, COLUMNS, null, null, null) } ?: throw FolderGoneException()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                kids[key(name)] = Child(it.getString(0), name, it.getString(2) == Document.MIME_TYPE_DIR)
            }
        }
        kids
    }

    private fun create(parentId: String, mime: String, name: String): String {
        val created = guarded { DocumentsContract.createDocument(resolver, uriOf(parentId), mime, name) } ?: throw FolderGoneException()
        return DocumentsContract.getDocumentId(created)
    }

    /** A tree that was removed or lost its grant answers with one of these. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (_: SecurityException) {
        throw FolderGoneException()
    } catch (_: FileNotFoundException) {
        throw FolderGoneException()
    } catch (_: IllegalArgumentException) {
        throw FolderGoneException()
    }

    private fun uriOf(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    private fun key(name: String): String = name.lowercase(Locale.ROOT)

    private companion object {
        const val BINARY = "application/octet-stream"
        const val WRITE = "w"
        val COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    }
}
