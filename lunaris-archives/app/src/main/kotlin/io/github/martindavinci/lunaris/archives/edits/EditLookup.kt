package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.files.EditedCopies
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * A copy that was changed in another app is what opens or is shared again, never a fresh copy of the entry: the
 * live copy when it is still there, else the snapshot put back in its place.
 */
class EditLookup @Inject constructor(private val store: EditStore, private val watcher: EditWatcher) : EditedCopies {
    override suspend fun edited(copy: File): File? {
        val record = store.all().firstOrNull { it.copyPath == copy.path } ?: return null
        val looked = watcher.check(record) ?: return null
        val pending = looked.pending ?: return null
        if (copy.isFile) return copy
        return try {
            copy.parentFile?.mkdirs()
            File(pending.path).copyTo(copy, overwrite = true).also { it.setLastModified(pending.mtime) }
        } catch (_: IOException) {
            null
        }
    }
}
