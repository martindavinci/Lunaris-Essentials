package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The app's cache folders: swept at start-up, emptied from Settings. */
@Singleton
class CacheJanitor @Inject constructor(@param:CacheDir private val cacheDir: File) {
    /** Children of open/, share/ and incoming/ untouched for a day; index/ keeps its own limits. */
    fun sweep(now: Long) {
        SWEPT.forEach { folder ->
            File(cacheDir, folder).listFiles()?.forEach { child ->
                if (now - child.lastModified() > Limits.TEMP_MAX_AGE_MILLIS) child.deleteRecursively()
            }
        }
    }

    fun clearAll() {
        (SWEPT + INDEX).forEach { File(cacheDir, it).deleteRecursively() }
    }

    fun sizeBytes(): Long = (SWEPT + INDEX).sumOf { folder -> File(cacheDir, folder).walk().filter { it.isFile }.sumOf { it.length() } }

    private companion object {
        val SWEPT = listOf("open", "share", "incoming")
        const val INDEX = "index"
    }
}
