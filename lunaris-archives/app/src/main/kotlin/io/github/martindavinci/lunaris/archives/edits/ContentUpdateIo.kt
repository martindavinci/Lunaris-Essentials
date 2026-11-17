package io.github.martindavinci.lunaris.archives.edits

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** [UpdateIo] over the ContentResolver. Every provider failure is an exception the updater turns into a result. */
class ContentUpdateIo @Inject constructor(private val resolver: ContentResolver) : UpdateIo {
    override fun openInput(uri: String): InputStream = resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)

    override fun facts(uri: String): ArchiveFacts? = runCatching {
        resolver.openFileDescriptor(Uri.parse(uri), READ)?.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val size = channel.size()
                ArchiveFacts(size, null, Fingerprint.of(channel, size))
            }
        }
    }.getOrNull()

    override fun canWrite(uri: String): Boolean = runCatching {
        resolver.openFileDescriptor(Uri.parse(uri), READ_WRITE)?.use { true } ?: false
    }.getOrDefault(false)

    override fun keepAccess(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        if (resolver.persistedUriPermissions.any { it.uri == parsed && it.isWritePermission }) return false
        return runCatching { resolver.takePersistableUriPermission(parsed, FLAGS) }.isSuccess
    }

    override fun releaseAccess(uri: String) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), FLAGS) }
    }

    override fun openForReplace(uri: String): ReplaceTarget {
        val fd = resolver.openFileDescriptor(Uri.parse(uri), REPLACE) ?: throw FileNotFoundException(uri)
        val out = FileOutputStream(fd.fileDescriptor)
        return Target(fd, out)
    }

    private class Target(private val fd: ParcelFileDescriptor, override val stream: OutputStream) : ReplaceTarget {
        private val channel = (stream as FileOutputStream).channel

        override fun truncateHere() {
            runCatching { channel.truncate(channel.position()) }   // a pipe has no position: "t" did the job
        }

        override fun sync() {
            runCatching { fd.fileDescriptor.sync() }
        }

        override fun close() {
            runCatching { stream.close() }
            fd.close()
        }
    }

    private companion object {
        const val READ = "r"
        const val READ_WRITE = "rw"
        const val REPLACE = "rwt"
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
