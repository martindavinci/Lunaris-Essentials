package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PageSize(val width: Int, val height: Int)

data class PagePoints(val width: Float, val height: Float)

/** [pixels] are the paper at 300 dots per inch, the long side second; [points] are its size in a PDF. */
enum class PaperSize(val pixels: PageSize?, val points: PagePoints?) {
    AS_PHOTOGRAPHED(null, null),
    A4(PageSize(2480, 3508), PagePoints(595.28f, 841.89f)),
    LETTER(PageSize(2550, 3300), PagePoints(612f, 792f)),
}

private const val POINTS_PER_INCH = 72f
private const val DOTS_PER_INCH = 300f
private const val HALF = 2f

/** The page's pixels before it is turned: from the paper if known, or else from the corners' own proportions, which a steep angle shortens. */
internal fun pageSize(cornersInPixels: PageCorners, paper: PaperSize, longSide: Int): PageSize {
    val (topLeft, topRight, bottomRight, bottomLeft) = cornersInPixels
    val width = (distance(topLeft, topRight) + distance(bottomLeft, bottomRight)) / HALF
    val height = (distance(topLeft, bottomLeft) + distance(topRight, bottomRight)) / HALF
    val pixels = paper.pixels
    if (pixels != null) {
        // Never past the paper's own 300 dots per inch.
        val scale = min(longSide, pixels.height).toFloat() / pixels.height
        val short = (pixels.width * scale).roundToInt()
        val long = (pixels.height * scale).roundToInt()
        return if (width > height) PageSize(long, short) else PageSize(short, long)
    }
    val longest = max(width, height)
    val scale = min(longSide.toFloat(), longest) / longest
    return PageSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
}

/** The size in a PDF of a page of [size] pixels, already turned. */
internal fun pagePoints(size: PageSize, paper: PaperSize): PagePoints {
    val points = paper.points ?: return PagePoints(
        size.width * POINTS_PER_INCH / DOTS_PER_INCH,
        size.height * POINTS_PER_INCH / DOTS_PER_INCH,
    )
    return if (size.width > size.height) PagePoints(points.height, points.width) else points
}

private fun distance(a: PagePoint, b: PagePoint): Float = hypot(a.x - b.x, a.y - b.y)
