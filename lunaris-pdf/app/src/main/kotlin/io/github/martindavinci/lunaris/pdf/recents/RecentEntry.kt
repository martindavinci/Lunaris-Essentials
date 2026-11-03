package io.github.martindavinci.lunaris.pdf.recents

import io.github.martindavinci.lunaris.pdf.model.OpenDocument

/**
 * A document read before: where it is and where reading stopped. [chapter] is the one piece of its
 * content kept here, and it stays empty for a file with a password ([locked]).
 */
data class RecentEntry(
    val uri: String,
    val name: String = "",
    val page: Int = 1,
    val scale: String = OpenDocument.DEFAULT_SCALE,
    val lastOpenedMillis: Long = 0,
    val pages: Int = 0,
    val chapter: String = "",
    val locked: Boolean = false,
    val pinned: Boolean = false,
)

/** A document that has just opened. */
data class RecentVisit(val uri: String, val name: String, val pages: Int = 0, val locked: Boolean = false)

/** Where reading stands now. */
data class ReadingSpot(val uri: String, val page: Int, val scale: String, val chapter: String)
