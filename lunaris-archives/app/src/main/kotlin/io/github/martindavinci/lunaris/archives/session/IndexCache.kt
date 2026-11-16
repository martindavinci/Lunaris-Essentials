package io.github.martindavinci.lunaris.archives.session

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.WireArchive
import io.github.martindavinci.lunaris.archives.engine.WireCollector
import io.github.martindavinci.lunaris.archives.engine.WireEntry
import io.github.martindavinci.lunaris.archives.engine.WireReader
import io.github.martindavinci.lunaris.archives.engine.WireWriter
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexes of archives already listed, in cache/index/<key>.idx: one route byte, then the same
 * wire stream the engine sends. Read back through WireCollector, so a cached index passes the
 * same path rules and caps as a fresh one.
 *
 * [remove] breaks a loop: when a tap finds that the archive is no longer the one that was listed
 * (PATH_MISMATCH), the session drops this file before it lists again. With a provider that
 * reports no size or date the key stays the same, and the stale index would come straight back.
 */
@Singleton
class IndexCache @Inject constructor(@CacheDir cacheDir: File) {
    private val dir = File(cacheDir, FOLDER)

    fun read(key: String): Pair<EngineRoute, ArchiveIndex>? {
        val file = fileOf(key)?.takeIf { it.isFile } ?: return null
        return try {
            file.inputStream().buffered().use { input ->
                val route = EngineRoute.of(input.read())
                val collected = WireCollector.index(WireReader(input), ArchiveKind.UNKNOWN)
                val index = collected.index?.takeIf { collected.status == EngineStatus.OK } ?: throw IOException("no index")
                file.setLastModified(System.currentTimeMillis())   // most recently used stays longest
                route to index
            }
        } catch (_: IOException) {
            file.delete()
            null
        } catch (_: NoSuchElementException) {
            file.delete()
            null
        }
    }

    fun write(key: String, route: EngineRoute, index: ArchiveIndex) {
        if (index.encryption == Encryption.HEADERS) return   // the names are the secret
        val file = fileOf(key) ?: return
        val temp = File(dir, file.name + TEMP_SUFFIX)
        try {
            dir.mkdirs()
            temp.outputStream().use { out ->
                out.write(route.code)
                WireWriter(out).use { wire ->
                    index.entries.forEachIndexed { i, entry -> wire.entry(entry.toWire(index.createdBy.takeIf { i == 0 })) }
                    wire.archive(
                        WireArchive(
                            index.kind, index.formatLabel.startsWith(TAR_PREFIX), index.encryption,
                            index.comment?.toByteArray(Charsets.UTF_8), index.cappedAtLimit, index.partial,
                        ),
                    )
                    wire.status(EngineStatus.OK)
                }
            }
            if (!temp.renameTo(file)) temp.delete()   // a half-written file is never read
            trim()
        } catch (_: IOException) {
            temp.delete()
        }
    }

    fun remove(key: String) {
        fileOf(key)?.delete()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** At most [Limits.INDEX_CACHE_FILES] files and [Limits.INDEX_CACHE_BYTES]: the oldest go first. */
    private fun trim() {
        val files = dir.listFiles { file -> file.name.endsWith(SUFFIX) }?.sortedByDescending { it.lastModified() } ?: return
        var bytes = 0L
        files.forEachIndexed { i, file ->
            bytes += file.length()
            if (i >= Limits.INDEX_CACHE_FILES || bytes > Limits.INDEX_CACHE_BYTES) file.delete()
        }
    }

    /** Keys are ours (16 hex characters); anything else never becomes a file name. */
    private fun fileOf(key: String): File? = if (KEY.matches(key)) File(dir, key + SUFFIX) else null

    private fun ArchiveEntry.toWire(host: HostOs?) = WireEntry(
        index, rawPath, pathIsUtf8, type, size, packedSize, modifiedMillis, encrypted, crc, null, host,
    )

    private companion object {
        const val FOLDER = "index"
        const val SUFFIX = ".idx"
        const val TEMP_SUFFIX = ".tmp"
        const val TAR_PREFIX = "TAR."
        val KEY = Regex("[0-9a-f]{16}")
    }
}
