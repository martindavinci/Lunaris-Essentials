package io.github.martindavinci.lunaris.archives.session

import java.security.MessageDigest

/** A short key for an archive as it is now: a different size or date gives a different key. */
object ArchiveKeys {
    private const val KEY_BYTES = 8
    private const val UNKNOWN = -1L
    private const val SEPARATOR = '\u0000'

    fun of(uri: String, size: Long?, modified: Long?): String {
        val text = "$uri$SEPARATOR${size ?: UNKNOWN}$SEPARATOR${modified ?: UNKNOWN}"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.copyOf(KEY_BYTES).joinToString("") { "%02x".format(it) }
    }
}
