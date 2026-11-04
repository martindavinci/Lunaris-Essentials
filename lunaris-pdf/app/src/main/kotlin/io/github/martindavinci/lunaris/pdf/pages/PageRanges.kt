package io.github.martindavinci.lunaris.pdf.pages

/** Pages as people write them: `1-3, 5, 7-`. One-based in the text, zero-based out of it. */
object PageRanges {
    private val DASHES = charArrayOf('-', '–', '—')

    /** Null if any part is malformed or beyond the document. */
    fun parse(text: String, pageCount: Int): List<IntRange>? {
        val parts = text.split(',').map { it.trim() }
        if (parts.any { it.isEmpty() }) return null
        return parts.map { part(it, pageCount) ?: return null }
    }

    fun pageCount(ranges: List<IntRange>): Int = ranges.sumOf { it.last - it.first + 1 }

    private fun part(text: String, pageCount: Int): IntRange? {
        val ends = text.split(*DASHES).map { it.trim() }
        val range = when (ends.size) {
            1 -> ends[0].toIntOrNull()?.let { it..it }
            2 -> between(ends[0], ends[1], pageCount)
            else -> null
        } ?: return null
        if (range.first < 1 || range.last > pageCount || range.first > range.last) return null
        return range.first - 1..range.last - 1
    }

    private fun between(from: String, to: String, pageCount: Int): IntRange? {
        if (from.isEmpty() && to.isEmpty()) return null
        val first = if (from.isEmpty()) 1 else from.toIntOrNull()
        val last = if (to.isEmpty()) pageCount else to.toIntOrNull()
        return if (first == null || last == null) null else first..last
    }
}
