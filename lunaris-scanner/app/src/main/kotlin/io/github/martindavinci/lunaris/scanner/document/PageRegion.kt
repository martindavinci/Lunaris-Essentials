package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BYTE = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f

/** Colour differences count for more than brightness: a page and a table may be equally bright. */
private const val COLOUR_EDGE_WEIGHT = 1.5f
private const val PAPER_PERCENTILE = 0.85f
private const val NEAR_PAPER_BRIGHTNESS = 12f

/** Paper in shadow is still paper, down to this share of its brightness. */
private const val SHADOWED_PAPER = 0.42f
private const val BRIGHTER_THAN_PAPER = 40f
private const val SAME_PAPER_COLOUR = 28f
private const val GAPS_IN_THE_TEXT = 5

/** The colour the page seems to be. */
internal class PaperColour(val brightness: Float, val blue: Float, val red: Float)

/** A small copy of a photo, as brightness and two colour differences, with how sharply each point changes. */
internal class SmallPhoto private constructor(val width: Int, val height: Int, val y: FloatArray, val cb: FloatArray, val cr: FloatArray) {
    val edges = FloatArray(width * height).also { out ->
        val first = gradient(y)
        val second = gradient(cb)
        val third = gradient(cr)
        out.indices.forEach { i -> out[i] = max(first[i], COLOUR_EDGE_WEIGHT * max(second[i], third[i])) }
    }

    /** The bright end of the middle of the photo is taken for paper: the ink is dark and the table lies further out. */
    fun paper(): PaperColour {
        val window = middleOf(width, height)
        val sorted = window.map { y[it] }.sorted()
        val top = sorted[(sorted.size * PAPER_PERCENTILE).toInt().coerceAtMost(sorted.size - 1)]
        val near = window.filter { abs(y[it] - top) < NEAR_PAPER_BRIGHTNESS }
        return PaperColour(top, median(near.map { cb[it] }), median(near.map { cr[it] }))
    }

    /** What looks like paper and is not cut off from the middle of the photo by an edge stronger than [barrier]. */
    fun region(barrier: Float, paper: PaperColour): Mask? {
        val like = BooleanArray(y.size) { i ->
            y[i] > paper.brightness * SHADOWED_PAPER &&
                y[i] < paper.brightness + BRIGHTER_THAN_PAPER &&
                abs(cb[i] - paper.blue) + abs(cr[i] - paper.red) < SAME_PAPER_COLOUR &&
                edges[i] < barrier
        }
        val part = Mask(like, width, height).eroded(1).dilated(1).biggestNearCentre() ?: return null
        // Text cuts the paper up: close the gaps, then fill what is enclosed.
        return part.dilated(GAPS_IN_THE_TEXT).eroded(GAPS_IN_THE_TEXT).biggestNearCentre()?.filled()
    }

    private fun gradient(channel: FloatArray) = FloatArray(channel.size) { i ->
        val x = i % width
        val row = i / width
        val across = if (x in 1 until width - 1) abs(channel[i + 1] - channel[i - 1]) else 0f
        val down = if (row in 1 until height - 1) abs(channel[i + width] - channel[i - width]) else 0f
        across + down
    }

    companion object {
        fun of(photo: PagePixels, longSide: Int): SmallPhoto {
            val scale = min(1f, longSide.toFloat() / max(photo.width, photo.height))
            val width = max(1, (photo.width * scale).roundToInt())
            val height = max(1, (photo.height * scale).roundToInt())
            val y = FloatArray(width * height)
            val cb = FloatArray(width * height)
            val cr = FloatArray(width * height)
            for (row in 0 until height) {
                val top = row * photo.height / height
                val bottom = max(top + 1, (row + 1) * photo.height / height)
                for (column in 0 until width) {
                    val left = column * photo.width / width
                    val right = max(left + 1, (column + 1) * photo.width / width)
                    var red = 0f
                    var green = 0f
                    var blue = 0f
                    for (sy in top until bottom) {
                        for (sx in left until right) {
                            val pixel = photo.pixels[sy * photo.width + sx]
                            red += pixel shr RED_SHIFT and BYTE
                            green += pixel shr GREEN_SHIFT and BYTE
                            blue += pixel and BYTE
                        }
                    }
                    val count = (bottom - top) * (right - left)
                    val bright = (RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue) / count
                    val at = row * width + column
                    y[at] = bright
                    cb[at] = blue / count - bright
                    cr[at] = red / count - bright
                }
            }
            return SmallPhoto(width, height, smooth(y, width, height), smooth(cb, width, height), smooth(cr, width, height))
        }

        private fun smooth(channel: FloatArray, width: Int, height: Int) =
            boxMean(boxMean(channel, width, height, 1), width, height, 1)

        private fun median(values: List<Float>): Float = values.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    }
}
