package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import io.github.martindavinci.lunaris.archives.safety.SafePath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Copies of single entries for other apps: cache/open/<archive key>/<index>-<crc or date>/<name>. */
@Singleton
class TempCopies(private val cacheDir: File, private val capBytes: Long) {
    @Inject constructor(@CacheDir cacheDir: File) : this(cacheDir, Limits.OPEN_DIR_CAP_BYTES)

    private val root get() = File(cacheDir, FOLDER)

    fun target(archiveKey: String, entry: ArchiveEntry): File {
        val path = entry.path as? SafePath.Result.Ok ?: throw IllegalArgumentException("unsafe entry ${entry.index}")
        val leaf = SafeComponent.leaf(path) ?: throw IllegalArgumentException("no writable name for ${entry.index}")
        val version = entry.crc?.let { "%x".format(it) } ?: "t${entry.modifiedMillis ?: 0}"
        return File(File(File(root, archiveKey), "${entry.index}-$version"), leaf)
    }

    fun existingCopy(archiveKey: String, entry: ArchiveEntry): File? =
        target(archiveKey, entry).takeIf { it.isFile && entry.size != null && it.length() == entry.size }

    /** Copies older than a day go, then the oldest until the folder is under the cap; [keep] stays. */
    fun trim(keep: File, now: Long) {
        val copies = root.walk().filter { it.isFile && it != keep }.sortedBy { it.lastModified() }.toMutableList()
        copies.filter { now - it.lastModified() > Limits.TEMP_MAX_AGE_MILLIS }.forEach { drop(it); copies.remove(it) }
        var total = copies.sumOf { it.length() } + keep.length()
        for (file in copies) {
            if (total <= capBytes) break
            total -= file.length()
            drop(file)
        }
    }

    fun clearAll() {
        root.deleteRecursively()
    }

    /** The file and its entry folder (one copy per folder). */
    private fun drop(file: File) {
        file.parentFile?.deleteRecursively() ?: file.delete()
    }

    private companion object { const val FOLDER = "open" }
}
