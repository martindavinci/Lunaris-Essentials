package tools.lunaris.texteditor.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Queries/requests All-files access and manages persisted SAF tree grants. */
@Singleton
class StorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * SAF *tree* uris this app holds a persisted read grant for. Single-file document
     * grants (persisted when opening a file so recents survive restarts) are excluded —
     * they are not browsable folders and would crash DocumentFile.fromTreeUri.
     */
    fun grantedTrees(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }

    /** Persist read+write access to a SAF tree picked by the user; never crashes. */
    fun persistTree(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    /** Release a previously persisted SAF tree grant; never crashes. */
    fun releaseTree(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
    }
}
