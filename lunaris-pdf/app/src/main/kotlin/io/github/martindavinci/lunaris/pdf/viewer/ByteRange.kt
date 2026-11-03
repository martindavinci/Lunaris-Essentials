package io.github.martindavinci.lunaris.pdf.viewer

/** A single range of bytes of a file, both ends included. */
data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1

    companion object {
        private val HEADER = Regex("""bytes=(\d*)-(\d*)""")

        /** The range an HTTP `Range` header asks of a file of [size] bytes, or null if there is none to serve. */
        fun parse(header: String?, size: Long): ByteRange? {
            val match = header?.let { HEADER.matchEntire(it) } ?: return null
            if (size <= 0) return null
            val (first, last) = match.destructured
            return when {
                first.isEmpty() -> suffix(last.toLongOrNull(), size)
                else -> between(first.toLongOrNull(), last, size)
            }
        }

        private fun suffix(count: Long?, size: Long): ByteRange? =
            count?.takeIf { it > 0 }?.let { ByteRange((size - it).coerceAtLeast(0), size - 1) }

        private fun between(start: Long?, last: String, size: Long): ByteRange? {
            if (start == null || start >= size) return null
            val end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.coerceAtMost(size - 1) ?: return null
            return if (end < start) null else ByteRange(start, end)
        }
    }
}
