package io.github.martindavinci.lunaris.pdf.viewer

/** Where a page falls in the contents of its document. */
object DocumentChapters {
    /** The last entry at or before [page], at the shallowest level the document has. */
    fun chapterAt(outline: List<OutlineItem>, page: Int): OutlineItem? {
        val top = outline.minOfOrNull { it.depth } ?: return null
        return outline.lastOrNull { it.depth == top && it.page != null && it.page <= page }
    }

    /** The index of the last entry of any level at or before [page], or -1. */
    fun currentIndex(outline: List<OutlineItem>, page: Int): Int =
        outline.indexOfLast { it.page != null && it.page <= page }
}
