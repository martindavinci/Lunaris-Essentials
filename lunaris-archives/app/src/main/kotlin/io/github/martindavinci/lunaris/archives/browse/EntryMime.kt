package io.github.martindavinci.lunaris.archives.browse

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * The MIME type handed to another app: our table, then the platform's ([platform] wraps
 * MimeTypeMap so tests need no Android), then a look at the first bytes.
 */
object EntryMime {
    const val TEXT_PLAIN = "text/plain"
    const val OCTET_STREAM = "application/octet-stream"
    private const val NUL: Byte = 0

    private val TABLE = mapOf(
        "md" to "text/markdown", "markdown" to "text/markdown",
        "kt" to "text/x-kotlin", "kts" to "text/x-kotlin",
        "json" to "application/json",
        "yaml" to "application/x-yaml", "yml" to "application/x-yaml",
        "csv" to "text/csv",
        "toml" to TEXT_PLAIN, "ini" to TEXT_PLAIN, "conf" to TEXT_PLAIN, "cfg" to TEXT_PLAIN,
        "log" to TEXT_PLAIN, "properties" to TEXT_PLAIN, "gradle" to TEXT_PLAIN, "env" to TEXT_PLAIN,
        "zip" to "application/zip", "7z" to "application/x-7z-compressed", "rar" to "application/vnd.rar",
        "tar" to "application/x-tar", "gz" to "application/gzip", "tgz" to "application/gzip",
        "bz2" to "application/x-bzip2", "xz" to "application/x-xz", "zst" to "application/zstd",
        "lz4" to "application/x-lz4", "iso" to "application/x-iso9660-image", "cpio" to "application/x-cpio",
    )

    fun of(name: String, head: ByteArray?, platform: (String) -> String?): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            TABLE[ext]?.let { return it }
            platform(ext)?.let { return it }
        }
        return if (head != null && looksLikeText(head)) TEXT_PLAIN else OCTET_STREAM
    }

    /** Valid UTF-8 without NUL in the first bytes; a character cut by the end of the sample is fine. */
    private fun looksLikeText(head: ByteArray): Boolean {
        val length = minOf(head.size, Limits.TEXT_SNIFF_BYTES)
        if ((0 until length).any { head[it] == NUL }) return false
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(head, 0, length), CharBuffer.allocate(length), false)
        return !result.isError
    }
}
