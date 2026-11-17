package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import io.github.martindavinci.lunaris.archives.safety.SafePath
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Copies of single entries for other apps: cache/open/<archive key>/<index>-<crc or date>/<name>. */
@Singleton
class TempCopies(private val cacheDir: File, private val capBytes: Long, private val protected: ProtectedCopies = ProtectedCopies.NONE) {
    @Inject constructor(@CacheDir cacheDir: File, protected: ProtectedCopies = ProtectedCopies.NONE) : this(cacheDir, Limits.OPEN_DIR_CAP_BYTES, protected)

    private val root get() = File(cacheDir, FOLDER)

    /** A name made only of dots and spaces has no writable form: its copy is called [FALLBACK_LEAF]. */
    fun target(archiveKey: String, entry: ArchiveEntry): File {
        val path = entry.path as? SafePath.Result.Ok ?: throw IllegalArgumentException("unsafe entry ${entry.index}")
        val leaf = SafeComponent.leaf(path) ?: FALLBACK_LEAF
        val version = entry.crc?.let { "%x".format(it) } ?: "t${entry.modifiedMillis ?: 0}"
        return File(File(File(root, archiveKey), "${entry.index}-$version"), leaf)
    }

    fun existingCopy(archiveKey: String, entry: ArchiveEntry): File? =
        target(archiveKey, entry).takeIf { it.isFile && entry.size != null && it.length() == entry.size }

    /** A folder for one copy in progress, beside the folder the finished file will be in. */
    fun attempt(target: File): File {
        val folder = requireNotNull(target.parentFile) { "no folder for $target" }
        return File(folder.parentFile, folder.name + ATTEMPT_MARK + UUID.randomUUID())
    }

    /** Moves a finished attempt into place. When another copy of the entry got there first, that one is used. */
    @Synchronized
    fun publish(attempt: File, target: File, entry: ArchiveEntry): File? {
        val folder = target.parentFile ?: return null
        if (protected.guards(folder)) {   // another app is editing that copy: it is never replaced
            attempt.deleteRecursively()
            return target.takeIf { it.isFile }
        }
        val whole = target.isFile && entry.size != null && target.length() == entry.size
        if (!whole) {
            folder.deleteRecursively()
            attempt.renameTo(folder)
        }
        attempt.deleteRecursively()
        return target.takeIf { it.isFile }
    }

    /** Copies older than a day go, then the oldest until the folder is under the cap; the files of [keep] stay. */
    fun trim(keep: Collection<File>, now: Long) {
        val kept = keep.toHashSet() + protected.files()
        val copies = root.walk().filter { it.isFile && it !in kept && ATTEMPT_MARK !in it.parentFile.name }
            .sortedBy { it.lastModified() }.toMutableList()
        copies.filter { now - it.lastModified() > Limits.TEMP_MAX_AGE_MILLIS }.forEach { drop(it); copies.remove(it) }
        var total = copies.sumOf { it.length() } + kept.sumOf { it.length() }
        for (file in copies) {
            if (total <= capBytes) break
            total -= file.length()
            drop(file)
        }
    }

    /** Everything but the copies another app may still be writing. */
    fun clearAll() {
        val guarded = protected.files()
        if (guarded.isEmpty()) {
            root.deleteRecursively()
            return
        }
        root.walkBottomUp().filter { it != root && !protected.guards(it, guarded) }.forEach { it.delete() }
    }

    /** The file and its entry folder (one copy per folder). */
    private fun drop(file: File) {
        file.parentFile?.deleteRecursively() ?: file.delete()
    }

    private companion object {
        const val FOLDER = "open"
        const val FALLBACK_LEAF = "file"
        const val ATTEMPT_MARK = ".part-"
    }
}
