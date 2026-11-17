package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The app's cache folders: swept at start-up, emptied from Settings. */
@Singleton
class CacheJanitor @Inject constructor(
    @param:CacheDir private val cacheDir: File,
    private val protected: ProtectedCopies = ProtectedCopies.NONE,
) {
    /** Children of the work folders untouched for a day; index/ keeps its own limits. Copies being edited stay. */
    fun sweep(now: Long) {
        val guarded = protected.files()
        SWEPT.forEach { folder -> sweepIn(File(cacheDir, folder), now, guarded) }
    }

    fun clearAll() {
        val guarded = protected.files()
        (SWEPT + INDEX).forEach { folder ->
            val dir = File(cacheDir, folder)
            if (!protected.guards(dir, guarded)) dir.deleteRecursively()
            else dir.walkBottomUp().filter { it != dir && !protected.guards(it, guarded) }.forEach { it.delete() }
        }
    }

    /** An old child goes whole unless it holds a guarded copy: then its own children are looked at. */
    private fun sweepIn(dir: File, now: Long, guarded: Set<File>) {
        dir.listFiles()?.forEach { child ->
            when {
                protected.guards(child, guarded) -> if (child.isDirectory) sweepIn(child, now, guarded)
                now - child.lastModified() > Limits.TEMP_MAX_AGE_MILLIS -> child.deleteRecursively()
            }
        }
    }

    fun sizeBytes(): Long = (SWEPT + INDEX).sumOf { folder -> File(cacheDir, folder).walk().filter { it.isFile }.sumOf { it.length() } }

    private companion object {
        val SWEPT = listOf("open", "share", "incoming", "create", "update")
        const val INDEX = "index"
    }
}
