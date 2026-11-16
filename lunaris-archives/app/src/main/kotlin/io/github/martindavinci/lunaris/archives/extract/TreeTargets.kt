package io.github.martindavinci.lunaris.archives.extract

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Folders picked for extraction: the latest keeps its permission, older ones are let go. */
@Singleton
class TreeTargets @Inject constructor(@param:ApplicationContext private val context: android.content.Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Where the picker should start: the folder used last time, when its permission is still held. */
    fun last(): Uri? = resolver.persistedUriPermissions.map { it.uri }.lastOrNull(::isTree)

    fun open(tree: Uri): SafTarget {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(tree, flags) }
        resolver.persistedUriPermissions.filter { isTree(it.uri) && it.uri != tree }.forEach { old ->
            runCatching { resolver.releasePersistableUriPermission(old.uri, flags) }
        }
        return DocumentsTarget(resolver, tree)
    }

    private fun isTree(uri: Uri) = DocumentsContract.isTreeUri(uri)
}
