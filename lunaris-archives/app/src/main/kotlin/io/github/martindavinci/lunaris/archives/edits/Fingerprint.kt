package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** Recognising a file again: an archive by its size and edges, a copy by its whole content. */
object Fingerprint {
    /** SHA-256 of the size and of the first and last [Limits.FINGERPRINT_EDGE_BYTES]: the same file under another URI. */
    fun of(channel: FileChannel, size: Long): String {
        val digest = MessageDigest.getInstance(SHA_256)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
        val edge = Limits.FINGERPRINT_EDGE_BYTES.toLong()
        feed(digest, channel, 0, minOf(edge, size))
        if (size > edge) feed(digest, channel, maxOf(edge, size - edge), size)
        return digest.digest().hex()
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance(SHA_256)
        val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().hex()
    }

    private fun feed(digest: MessageDigest, channel: FileChannel, from: Long, to: Long) {
        val buffer = ByteBuffer.allocate(Limits.WIRE_CHUNK_BYTES)
        var position = from
        while (position < to) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity().toLong(), to - position).toInt())
            val n = channel.read(buffer, position)
            if (n <= 0) break
            digest.update(buffer.array(), 0, n)
            position += n
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private const val SHA_256 = "SHA-256"
}
