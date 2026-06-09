package tools.lunaris.texteditor.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Queries/requests All-files access and manages persisted SAF tree grants. */
@Singleton
class StorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True if the app holds MANAGE_EXTERNAL_STORAGE (All-files). */
    fun hasAllFiles(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** Intent that opens the system "All files access" settings page for this app. */
    fun allFilesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName),
        )

    /** Default browsing root for All-files mode. */
    fun externalRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    /** SAF tree uris this app holds a persisted read grant for. */
    fun grantedTrees(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
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
