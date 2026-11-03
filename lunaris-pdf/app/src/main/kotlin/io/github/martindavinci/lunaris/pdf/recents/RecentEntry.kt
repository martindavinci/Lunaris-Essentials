package io.github.martindavinci.lunaris.pdf.recents

import io.github.martindavinci.lunaris.pdf.model.OpenDocument

/** A document read before. Only where it is and where reading stopped: nothing of its content. */
data class RecentEntry(
    val uri: String,
    val name: String = "",
    val page: Int = 1,
    val scale: String = OpenDocument.DEFAULT_SCALE,
    val lastOpenedMillis: Long = 0,
)
