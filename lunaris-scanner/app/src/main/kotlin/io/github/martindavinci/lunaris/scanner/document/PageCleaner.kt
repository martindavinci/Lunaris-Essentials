package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class CleanMode { COLOUR, GREY, BLACK_AND_WHITE, NONE }

private const val LEVELS = 256
private const val TOP_LEVEL = 255
private const val OPAQUE = 0xFF shl 24
private const val BYTE = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** Below this a light is too dim to divide by: what is really dark must stay dark. */
private const val MIN_LIGHT = 24f

/** From here up it is paper, and goes to white. Below, the power darkens the ink. */
private const val PAPER_FROM = 0.9f
private const val INK_POWER = 1.6

private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f

private const val OTSU_START = 128

private const val CURVE_STEPS = 1023
private val CURVE = IntArray(CURVE_STEPS + 1) { step ->
    val share = step.toFloat() / CURVE_STEPS
    if (share >= PAPER_FROM) TOP_LEVEL else (TOP_LEVEL * (share / PAPER_FROM).toDouble().pow(INK_POWER)).roundToInt()
}

private fun curve(share: Float): Int = CURVE[(share * CURVE_STEPS).toInt()]

private fun grey(level: Int): Int = OPAQUE or (level shl RED_SHIFT) or (level shl GREEN_SHIFT) or level

/**
 * Whitens the paper in place: each colour is divided by the light that fell there, so shadows and a
 * lamp's colour go, then a curve darkens the ink. A photo on the page is lightened too, because it
 * looks like a shadow: that is what [CleanMode.NONE] is for.
 */
internal fun cleanPage(page: PagePixels, mode: CleanMode) {
    if (mode == CleanMode.NONE) return
    val lighting = PageLighting.of(page)
    val histogram = IntArray(LEVELS)
    val pixels = page.pixels
    for (y in 0 until page.height) {
        lighting.moveToRow(y)
        val row = y * page.width
        for (x in 0 until page.width) {
            val pixel = pixels[row + x]
            val red = share(pixel shr RED_SHIFT and BYTE, lighting.at(0, x))
            val green = share(pixel shr GREEN_SHIFT and BYTE, lighting.at(1, x))
            val blue = share(pixel and BYTE, lighting.at(2, x))
            pixels[row + x] = if (mode == CleanMode.COLOUR) {
                OPAQUE or (curve(red) shl RED_SHIFT) or (curve(green) shl GREEN_SHIFT) or curve(blue)
            } else {
                val brightness = RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
                if (mode == CleanMode.GREY) {
                    grey(curve(brightness))
                } else {
                    val level = min(TOP_LEVEL, (brightness * TOP_LEVEL).roundToInt())
                    histogram[level]++
                    grey(level)
                }
            }
        }
    }
    if (mode == CleanMode.BLACK_AND_WHITE) {
        val threshold = otsuThreshold(histogram, pixels.size)
        for (index in pixels.indices) {
            pixels[index] = grey(if (pixels[index] and BYTE < threshold) 0 else TOP_LEVEL)
        }
    }
}

private fun share(value: Int, light: Float): Float = min(1f, value / max(MIN_LIGHT, light))

/** The threshold that best separates two tones: Otsu's method, which maximises the variance between them. */
internal fun otsuThreshold(histogram: IntArray, total: Int): Int {
    var sum = 0L
    for (level in histogram.indices) sum += level.toLong() * histogram[level]
    var darkSum = 0L
    var darkCount = 0
    var best = 0.0
    var threshold = OTSU_START
    for (level in histogram.indices) {
        darkCount += histogram[level]
        if (darkCount == 0) continue
        val lightCount = total - darkCount
        if (lightCount == 0) break
        darkSum += level.toLong() * histogram[level]
        val darkMean = darkSum.toDouble() / darkCount
        val lightMean = (sum - darkSum).toDouble() / lightCount
        val gap = darkMean - lightMean
        val between = darkCount.toDouble() * lightCount * gap * gap
        if (between > best) {
            best = between
            threshold = level + 1
        }
    }
    return threshold
}
