package io.github.martindavinci.lunaris.pdf.feature.reader

import kotlin.math.abs

/** A long move through the document, and the way back to where reading was. */
data class PageJump(val returnTo: Int, val landedOn: Int, val forward: Boolean = false) {

    /** Null once reading has moved on from where the jump landed. */
    fun whileReading(page: Int): PageJump? = takeIf { isNear(page, landedOn) }

    fun reversed() = PageJump(returnTo = landedOn, landedOn = returnTo, forward = !forward)

    companion object {
        private const val NEARBY_PAGES = 2

        fun isNear(page: Int, other: Int): Boolean = abs(page - other) <= NEARBY_PAGES

        /** A short move is just reading, and leaves [current] as it is. */
        fun after(current: PageJump?, from: Int, to: Int): PageJump? =
            if (isNear(from, to)) current else PageJump(from, to)
    }
}
