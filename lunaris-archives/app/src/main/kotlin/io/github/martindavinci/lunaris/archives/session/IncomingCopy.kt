package io.github.martindavinci.lunaris.archives.session

import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One-time copies of archives that cannot be read at random (pipes, some cloud providers). */
@Singleton
class IncomingCopy @Inject constructor(@param:CacheDir private val cacheDir: File) {
    private val root get() = File(cacheDir, FOLDER)

    /** cache/incoming/<key>/<safe leaf name>. */
    fun target(key: String, name: String): File {
        val leaf = SafeComponent.forWriting(name.substringAfterLast('/').substringAfterLast('\\')) ?: FALLBACK_NAME
        return File(File(root, key), leaf)
    }

    /**
     * Copies [input] into [target]; on failure or cancellation the partial file is deleted.
     * A provider may not say how big the archive is: [freeBytes] is asked again every [checkEvery] bytes,
     * and the copy stops while the cache margin is still there.
     */
    suspend fun copy(
        input: InputStream, target: File, freeBytes: () -> Long = { Long.MAX_VALUE },
        checkEvery: Long = Limits.INCOMING_SPACE_CHECK_BYTES, onProgress: (Long) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            try {
                input.use { source ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
                        var copied = 0L
                        var nextCheck = checkEvery
                        while (true) {
                            ensureActive()
                            val n = source.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            if (copied >= nextCheck) {
                                nextCheck += checkEvery
                                val free = freeBytes()
                                if (free < Limits.CACHE_MARGIN_BYTES) throw NoSpaceException(copied + Limits.CACHE_MARGIN_BYTES, free)
                            }
                            onProgress(copied)
                        }
                    }
                }
                target
            } catch (e: Throwable) {
                target.delete()
                throw e
            }
        }

    fun forget(key: String) {
        File(root, key).deleteRecursively()
    }

    private companion object {
        const val FOLDER = "incoming"
        const val FALLBACK_NAME = "archive"
    }
}
