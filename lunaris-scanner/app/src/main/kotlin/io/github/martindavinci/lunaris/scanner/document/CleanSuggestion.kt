package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.min

private const val BYTE = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** Darker than this it is not paper. */
private const val PAPER_LEVEL = 225

/** Channels this far apart are a real colour, and not the faint tint a lens leaves at the edge of a stroke. */
private const val COLOURFUL = 48

/** More ink than this is no page of text: most likely a photograph. */
private const val MAX_INK_SHARE = 0.35f

/** Enough coloured points to be a stamp or a signature, and not stray specks. */
private const val MIN_COLOUR_SHARE = 0.001f
private const val MIN_COLOUR_OF_INK = 0.01f

/** The outline never fits to the pixel, so the rim may hold a bit of the table: it says nothing about the page. */
private const val RIM_SHARE = 0.05f

/**
 * What suits a page already cleaned in colour. Black and white is the sharpest and smallest for plain
 * text, but it would ruin a blue signature, a stamp or a photograph.
 */
internal fun suggestedClean(page: PagePixels): CleanMode {
    var ink = 0
    var colourful = 0
    val rimX = (page.width * RIM_SHARE).toInt()
    val rimY = (page.height * RIM_SHARE).toInt()
    for (y in rimY until page.height - rimY) {
        for (x in rimX until page.width - rimX) {
            val pixel = page.pixels[y * page.width + x]
            val red = pixel shr RED_SHIFT and BYTE
            val green = pixel shr GREEN_SHIFT and BYTE
            val blue = pixel and BYTE
            if (min(red, min(green, blue)) < PAPER_LEVEL) ink++
            if (max(red, max(green, blue)) - min(red, min(green, blue)) > COLOURFUL) colourful++
        }
    }
    val total = ((page.width - 2 * rimX) * (page.height - 2 * rimY)).toFloat()
    val colour = colourful / total >= MIN_COLOUR_SHARE && colourful >= MIN_COLOUR_OF_INK * ink
    return if (colour || ink / total > MAX_INK_SHARE) CleanMode.COLOUR else CleanMode.BLACK_AND_WHITE
}
