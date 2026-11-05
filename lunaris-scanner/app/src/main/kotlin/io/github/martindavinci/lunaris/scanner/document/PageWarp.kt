package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.abs
import kotlin.math.roundToInt

/** ARGB pixels of a photo or a page. Plain arrays, so the ViewModel layer stays free of Android. */
class PagePixels(val pixels: IntArray, val width: Int, val height: Int)

private const val UNKNOWNS = 8
private const val NOT_QUITE_THE_LAST = 1.001
private const val PIXEL_CENTRE = 0.5
private const val OPAQUE = 0xFF shl 24
private const val BYTE = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** The eight numbers of the perspective transform that carries a flat rectangle onto a quadrilateral. */
internal class PerspectiveMapping private constructor(private val h: DoubleArray) {
    fun x(u: Double, v: Double): Double = (h[0] * u + h[1] * v + h[2]) / (h[6] * u + h[7] * v + 1)

    fun y(u: Double, v: Double): Double = (h[3] * u + h[4] * v + h[5]) / (h[6] * u + h[7] * v + 1)

    companion object {
        /** From the rectangle with its corner at the origin onto [quad], corner by corner. */
        fun between(width: Int, height: Int, quad: PageCorners): PerspectiveMapping {
            val flat = listOf(0.0 to 0.0, width.toDouble() to 0.0, width.toDouble() to height.toDouble(), 0.0 to height.toDouble())
            val rows = mutableListOf<DoubleArray>()
            val results = mutableListOf<Double>()
            quad.points.forEachIndexed { index, point ->
                val (u, v) = flat[index]
                val x = point.x.toDouble()
                val y = point.y.toDouble()
                rows += doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -u * x, -v * x)
                results += x
                rows += doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -u * y, -v * y)
                results += y
            }
            return PerspectiveMapping(solved(rows, results))
        }

        /** Gaussian elimination with partial pivoting. */
        private fun solved(rows: MutableList<DoubleArray>, results: MutableList<Double>): DoubleArray {
            for (column in 0 until UNKNOWNS) {
                val pivot = (column until UNKNOWNS).maxBy { abs(rows[it][column]) }
                rows[column] = rows[pivot].also { rows[pivot] = rows[column] }
                results[column] = results[pivot].also { results[pivot] = results[column] }
                for (row in column + 1 until UNKNOWNS) {
                    val factor = rows[row][column] / rows[column][column]
                    for (k in column until UNKNOWNS) rows[row][k] -= factor * rows[column][k]
                    results[row] -= factor * results[column]
                }
            }
            val out = DoubleArray(UNKNOWNS)
            for (row in UNKNOWNS - 1 downTo 0) {
                var rest = results[row]
                for (k in row + 1 until UNKNOWNS) rest -= rows[row][k] * out[k]
                out[row] = rest / rows[row][row]
            }
            return out
        }
    }
}

/** Reads each pixel of a flat page of [width] by [height] back from [photo], from its four nearest neighbours. [quad] is in the photo's pixels. */
internal fun warp(photo: PagePixels, quad: PageCorners, width: Int, height: Int): PagePixels {
    val surface = PageSurface(width, height, quad)
    val out = IntArray(width * height)
    val source = photo.pixels
    val sourceWidth = photo.width
    val lastX = sourceWidth - NOT_QUITE_THE_LAST
    val lastY = photo.height - NOT_QUITE_THE_LAST
    for (v in 0 until height) {
        val vv = v + PIXEL_CENTRE
        for (u in 0 until width) {
            val uu = u + PIXEL_CENTRE
            val x = (surface.x(uu, vv) - PIXEL_CENTRE).coerceIn(0.0, lastX.coerceAtLeast(0.0))
            val y = (surface.y(uu, vv) - PIXEL_CENTRE).coerceIn(0.0, lastY.coerceAtLeast(0.0))
            val left = x.toInt()
            val top = y.toInt()
            val shareX = x - left
            val shareY = y - top
            val at = top * sourceWidth + left
            val right = if (shareX > 0) at + 1 else at
            val below = if (shareY > 0) sourceWidth else 0
            val a = source[at]
            val b = source[right]
            val c = source[at + below]
            val d = source[right + below]
            out[v * width + u] = OPAQUE or
                (blend(a, b, c, d, RED_SHIFT, shareX, shareY) shl RED_SHIFT) or
                (blend(a, b, c, d, GREEN_SHIFT, shareX, shareY) shl GREEN_SHIFT) or
                blend(a, b, c, d, 0, shareX, shareY)
        }
    }
    return PagePixels(out, width, height)
}

private fun blend(a: Int, b: Int, c: Int, d: Int, shift: Int, shareX: Double, shareY: Double): Int {
    val upperLeft = a shr shift and BYTE
    val lowerLeft = c shr shift and BYTE
    val upper = upperLeft + ((b shr shift and BYTE) - upperLeft) * shareX
    val lower = lowerLeft + ((d shr shift and BYTE) - lowerLeft) * shareX
    return (upper + (lower - upper) * shareY).roundToInt()
}
