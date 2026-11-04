package io.github.martindavinci.lunaris.pdf.pages

/** How a document is to be cut: one file per row of ranges, or one per page. Page numbers are those on screen now. */
data class SplitPlan(val rows: List<String>, val everyPage: Boolean = false) {

    fun countOf(row: Int, pageCount: Int): Int? = PageRanges.parse(rows[row], pageCount)?.let(PageRanges::pageCount)

    fun fileCount(pageCount: Int): Int = if (everyPage) pageCount else rows.size

    /** Null if any row is wrong, or there is none. */
    fun outputs(slots: List<PageSlot>): List<List<PageSlot>>? {
        if (everyPage) return slots.map { listOf(it) }
        if (rows.isEmpty()) return null
        return rows.map { row ->
            val ranges = PageRanges.parse(row, slots.size) ?: return null
            ranges.flatMap { range -> range.map { slots[it] } }
        }
    }

    fun fileNames(name: String, pageCount: Int): List<String> {
        val base = name.removeSuffix(PDF)
        return if (everyPage) List(pageCount) { "$base (${it + 1})$PDF" } else rows.map { "$base (${tidy(it)})$PDF" }
    }

    private fun tidy(row: String): String = row.split(',').joinToString(", ") { part -> part.filterNot(Char::isWhitespace) }

    private companion object {
        const val PDF = ".pdf"
    }
}
