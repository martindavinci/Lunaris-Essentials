package io.github.martindavinci.lunaris.archives.edits

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/** [UpdateIo] over plain files, with the failures a provider can have. URIs are "file:" + path. */
class FileUpdateIo : UpdateIo {
    var writable = true
    /** Throws after this many bytes of a replace (once). */
    var failAfterBytes: Long? = null
    /** Fails every replace, so that restoring fails too. */
    var alwaysFail = false
    /** Called on the first write of a replace. */
    var onWrite: (() -> Unit)? = null
    var kept = mutableListOf<String>()

    private fun file(uri: String) = File(uri.removePrefix("file:"))

    override fun openInput(uri: String): InputStream = file(uri).inputStream()

    override fun facts(uri: String): ArchiveFacts? {
        val f = file(uri).takeIf { it.isFile } ?: return null
        return RandomAccessFile(f, "r").use { ArchiveFacts(f.length(), f.lastModified(), Fingerprint.of(it.channel, f.length())) }
    }

    override fun canWrite(uri: String): Boolean = writable

    override fun keepAccess(uri: String): Boolean = kept.add(uri)

    override fun releaseAccess(uri: String) { kept.remove(uri) }

    override fun openForReplace(uri: String): ReplaceTarget {
        if (alwaysFail) throw IOException("provider gone")
        val raf = RandomAccessFile(file(uri), "rw").apply { setLength(0) }
        var written = 0L
        var first = true
        return object : ReplaceTarget {
            override val stream: OutputStream = object : OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (first) { first = false; onWrite?.invoke() }
                    failAfterBytes?.let { limit ->
                        if (written + len > limit) {
                            failAfterBytes = null
                            raf.write(b, off, (limit - written).toInt())
                            throw IOException("ENOSPC halfway")
                        }
                    }
                    raf.write(b, off, len)
                    written += len
                }
            }
            override fun truncateHere() = raf.setLength(raf.filePointer)
            override fun sync() = raf.fd.sync()
            override fun close() = raf.close()
        }
    }
}
