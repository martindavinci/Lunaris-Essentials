package io.github.martindavinci.lunaris.archives.archive

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.text.Normalizer

/** Raw name bytes with content equality (a ByteArray field would compare by reference). */
class RawPath private constructor(private val data: ByteArray) {
    /** A copy: callers may keep or change it. */
    val bytes: ByteArray get() = data.copyOf()
    val size: Int get() = data.size

    /** The first [max] bytes, for names too long to be worth keeping whole. */
    fun head(max: Int): RawPath = if (data.size <= max) this else RawPath(data.copyOf(max))

    /**
     * The same entry, as two readers may spell it: libarchive turns a name to NFC, `\` to `/`
     * (DOS hosts) and stops at a NUL, while zip4j, Commons Compress and lib-rar keep the stored
     * bytes. Names that are not UTF-8 must be byte for byte the same. Only for checking that an
     * entry is still the one listed: what is written always comes from the listing.
     */
    fun sameEntryAs(other: RawPath?): Boolean {
        if (other == null) return false
        if (this == other) return true
        val mine = comparable() ?: return false
        return mine == other.comparable()
    }

    private fun comparable(): String? {
        val end = data.indexOf(0).let { if (it < 0) data.size else it }
        val text = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data, 0, end)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        return Normalizer.normalize(text.replace('\\', '/'), Normalizer.Form.NFC)
    }

    override fun equals(other: Any?): Boolean = other is RawPath && data.contentEquals(other.data)
    override fun hashCode(): Int = data.contentHashCode()
    override fun toString(): String = "RawPath(${data.size} bytes)"

    companion object {
        fun of(bytes: ByteArray): RawPath = RawPath(bytes.copyOf())

        /** For an array nobody else holds (the wire reader's): no second copy. */
        internal fun owning(bytes: ByteArray): RawPath = RawPath(bytes)
    }
}
