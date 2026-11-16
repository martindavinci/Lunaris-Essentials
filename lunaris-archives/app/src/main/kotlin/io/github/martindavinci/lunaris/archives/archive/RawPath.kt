package io.github.martindavinci.lunaris.archives.archive

/** Raw name bytes with content equality (a ByteArray field would compare by reference). */
class RawPath private constructor(private val data: ByteArray) {
    /** A copy: callers may keep or change it. */
    val bytes: ByteArray get() = data.copyOf()
    val size: Int get() = data.size

    /** The first [max] bytes, for names too long to be worth keeping whole. */
    fun head(max: Int): RawPath = if (data.size <= max) this else RawPath(data.copyOf(max))

    override fun equals(other: Any?): Boolean = other is RawPath && data.contentEquals(other.data)
    override fun hashCode(): Int = data.contentHashCode()
    override fun toString(): String = "RawPath(${data.size} bytes)"

    companion object {
        fun of(bytes: ByteArray): RawPath = RawPath(bytes.copyOf())

        /** For an array nobody else holds (the wire reader's): no second copy. */
        internal fun owning(bytes: ByteArray): RawPath = RawPath(bytes)
    }
}
