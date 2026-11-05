package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CELLS_ON_LONG_SIDE = 400
private const val SPREAD_RADIUS = 3
private const val BLUR_RADIUS = 4
private const val CELL_CENTRE = 0.5f
private const val BYTE = 0xFF
private const val BITS_IN_BYTE = 8

/** Colour channels, in the order the shifts of an ARGB pixel go. */
internal const val CHANNELS = 3

/**
 * The light that fell on a page, worked out at low resolution: spreading the brightest nearby value
 * over the text erases it, which leaves the paper and its shadows, and smoothing evens out the rest.
 */
internal class PageLighting private constructor(
    private val channels: List<FloatArray>,
    private val cellsWide: Int,
    private val cellsHigh: Int,
    private val cellSize: Int,
    width: Int,
) {
    private val leftCell = IntArray(width)
    private val rightCell = IntArray(width)
    private val rightShare = FloatArray(width)
    private var upperRow = 0
    private var lowerRow = 0
    private var lowerShare = 0f

    init {
        for (x in 0 until width) {
            val position = cellPosition(x, cellsWide)
            leftCell[x] = position.toInt()
            rightCell[x] = min(cellsWide - 1, leftCell[x] + 1)
            rightShare[x] = position - leftCell[x]
        }
    }

    private fun cellPosition(pixel: Int, cells: Int): Float =
        ((pixel + CELL_CENTRE) / cellSize - CELL_CENTRE).coerceIn(0f, (cells - 1).toFloat())

    fun moveToRow(y: Int) {
        val position = cellPosition(y, cellsHigh)
        upperRow = position.toInt()
        lowerRow = min(cellsHigh - 1, upperRow + 1)
        lowerShare = position - upperRow
    }

    /** The light at [x] in the row of [moveToRow], with cells blended so that no grid shows. */
    fun at(channel: Int, x: Int): Float {
        val cells = channels[channel]
        val upperLeft = cells[upperRow * cellsWide + leftCell[x]]
        val lowerLeft = cells[lowerRow * cellsWide + leftCell[x]]
        val upper = upperLeft + (cells[upperRow * cellsWide + rightCell[x]] - upperLeft) * rightShare[x]
        val lower = lowerLeft + (cells[lowerRow * cellsWide + rightCell[x]] - lowerLeft) * rightShare[x]
        return upper + (lower - upper) * lowerShare
    }

    companion object {
        fun of(page: PagePixels): PageLighting {
            val cellSize = max(1, (max(page.width, page.height).toFloat() / CELLS_ON_LONG_SIDE).roundToInt())
            val wide = ceil(page.width.toFloat() / cellSize).toInt()
            val high = ceil(page.height.toFloat() / cellSize).toInt()
            val channels = List(CHANNELS) { channel ->
                val shift = (CHANNELS - 1 - channel) * BITS_IN_BYTE
                val spreadOut = spread(averaged(page, shift, cellSize, wide, high), wide, high, SPREAD_RADIUS, brightest = true)
                spread(spreadOut, wide, high, BLUR_RADIUS, brightest = false)
            }
            return PageLighting(channels, wide, high, cellSize, page.width)
        }

        private fun averaged(page: PagePixels, shift: Int, cellSize: Int, wide: Int, high: Int): FloatArray {
            val sums = FloatArray(wide * high)
            val counts = IntArray(wide * high)
            for (y in 0 until page.height) {
                val row = y / cellSize * wide
                for (x in 0 until page.width) {
                    val cell = row + x / cellSize
                    sums[cell] += (page.pixels[y * page.width + x] shr shift and BYTE).toFloat()
                    counts[cell]++
                }
            }
            for (cell in sums.indices) sums[cell] /= counts[cell]
            return sums
        }

        /** Over a square of the given radius, one direction at a time: the brightest value, or else the mean. */
        private fun spread(values: FloatArray, wide: Int, high: Int, radius: Int, brightest: Boolean): FloatArray {
            val across = FloatArray(values.size)
            for (y in 0 until high) {
                for (x in 0 until wide) {
                    across[y * wide + x] = pick(radius, wide, x, brightest) { values[y * wide + it] }
                }
            }
            val out = FloatArray(values.size)
            for (y in 0 until high) {
                for (x in 0 until wide) {
                    out[y * wide + x] = pick(radius, high, y, brightest) { across[it * wide + x] }
                }
            }
            return out
        }

        private inline fun pick(radius: Int, limit: Int, at: Int, brightest: Boolean, value: (Int) -> Float): Float {
            val from = max(0, at - radius)
            val to = min(limit - 1, at + radius)
            var best = 0f
            var sum = 0f
            for (k in from..to) {
                val current = value(k)
                if (current > best) best = current
                sum += current
            }
            return if (brightest) best else sum / (to - from + 1)
        }
    }
}
