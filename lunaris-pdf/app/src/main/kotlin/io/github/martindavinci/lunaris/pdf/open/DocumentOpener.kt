package io.github.martindavinci.lunaris.pdf.open

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import io.github.martindavinci.lunaris.pdf.url.withVisibleBidiControls
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepares a document for the Reader: its permission, its name, where reading resumes. */
@Singleton
class DocumentOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val current: CurrentDocument,
) {
    /** Never fails: a file that cannot be read gets its message in the Reader. */
    suspend fun openNew(uri: String) = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        current.set(OpenDocument(uri, displayName(parsed), canWrite = keepAccess(parsed)))
    }

    /** False if the file is gone or the permission to read it is lost. */
    suspend fun openRecent(entry: RecentEntry): Boolean = withContext(Dispatchers.IO) {
        val parsed = entry.uri.toUri()
        if (!canRead(parsed)) return@withContext false
        current.set(OpenDocument(entry.uri, entry.name, canWrite(parsed), entry.page, entry.scale))
        true
    }

    private fun canRead(uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, READ_ONLY)?.use { true } ?: false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    /** Asks to keep the permission across restarts, which only some providers allow. True if writing is allowed. */
    private fun keepAccess(uri: Uri): Boolean {
        if (take(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) return true
        take(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return false
    }

    private fun take(uri: Uri, flags: Int): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun canWrite(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun displayName(uri: Uri): String {
        val fromProvider = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: RuntimeException) {
            null
        }
        val name = fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
        return name?.takeIf { it.isNotBlank() }?.withVisibleBidiControls() ?: context.getString(R.string.document_no_name)
    }

    private companion object {
        const val READ_ONLY = "r"
    }
}
