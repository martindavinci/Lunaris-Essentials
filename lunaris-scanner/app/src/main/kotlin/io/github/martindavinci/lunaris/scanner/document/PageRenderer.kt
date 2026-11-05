package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.roundToInt

private const val TURNS = 4
private const val QUARTER = 1
private const val HALF_TURN = 2

/** What the user chose for a page. [turn] counts quarter turns clockwise, applied after straightening. */
data class PageSettings(
    val corners: PageCorners = PageCorners.START,
    val clean: CleanMode = CleanMode.COLOUR,
    val paper: PaperSize = PaperSize.AS_PHOTOGRAPHED,
    val turn: Int = 0,
) {
    fun turnedRight() = copy(turn = (turn + 1) % TURNS)

    fun turnedLeft() = copy(turn = (turn + TURNS - 1) % TURNS)
}

/**
 * Straightens, cleans and turns. [photo] may be a smaller copy, for a preview: the page's proportions
 * come from [workSize], the photo at the size the full page is made from.
 */
internal fun renderPage(photo: PagePixels, settings: PageSettings, workSize: PageSize, longSide: Int): PagePixels {
    val size = pageSize(settings.corners.inPixels(workSize.width, workSize.height), settings.paper, longSide)
    val flat = warp(photo, settings.corners.inPixels(photo.width, photo.height), size.width, size.height)
    cleanPage(flat, settings.clean)
    return turned(flat, settings.turn)
}

internal fun turned(page: PagePixels, quarters: Int): PagePixels {
    if (quarters == 0) return page
    val width = page.width
    val height = page.height
    val source = page.pixels
    return when (quarters) {
        QUARTER -> PagePixels(IntArray(source.size) { index -> source[(height - 1 - index % height) * width + index / height] }, height, width)
        HALF_TURN -> PagePixels(IntArray(source.size) { index -> source[source.size - 1 - index] }, width, height)
        else -> PagePixels(IntArray(source.size) { index -> source[(index % height) * width + width - 1 - index / height] }, height, width)
    }
}

/** A small copy with the same proportions, by taking every so many pixels: enough for a thumbnail. */
internal fun shrunk(page: PagePixels, longSide: Int): PagePixels {
    val longest = max(page.width, page.height)
    if (longest <= longSide) return page
    val width = max(1, (page.width.toLong() * longSide / longest).toInt())
    val height = max(1, (page.height.toLong() * longSide / longest).toInt())
    val stepX = page.width.toFloat() / width
    val stepY = page.height.toFloat() / height
    val out = IntArray(width * height) { index ->
        val x = ((index % width) * stepX).roundToInt().coerceAtMost(page.width - 1)
        val y = ((index / width) * stepY).roundToInt().coerceAtMost(page.height - 1)
        page.pixels[y * page.width + x]
    }
    return PagePixels(out, width, height)
}
