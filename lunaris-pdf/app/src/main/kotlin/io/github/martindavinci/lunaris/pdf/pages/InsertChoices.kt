package io.github.martindavinci.lunaris.pdf.pages

/** The places a new page can go: 0 is the start, n is after page n. A list of them all would not fit a long document. */
object InsertChoices {
    const val NEAR = 40

    fun around(current: Int, pageCount: Int): List<Int> {
        val near = (current - NEAR).coerceAtLeast(0)..(current + NEAR).coerceAtMost(pageCount)
        return (listOf(0) + near + pageCount).distinct().sorted()
    }
}
