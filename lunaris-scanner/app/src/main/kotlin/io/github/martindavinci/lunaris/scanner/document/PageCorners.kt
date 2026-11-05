package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

data class PagePoint(val x: Float, val y: Float)

enum class CornersProblem { CROSSED, TOO_SMALL }

/** How a photo sits on a stage: as large as fits, centred. */
data class PhotoFit(val left: Float, val top: Float, val scale: Float) {
    fun toStage(share: PagePoint, photoWidth: Int, photoHeight: Int) =
        PagePoint(left + share.x * photoWidth * scale, top + share.y * photoHeight * scale)

    fun toShare(stage: PagePoint, photoWidth: Int, photoHeight: Int) =
        PagePoint((stage.x - left) / (photoWidth * scale), (stage.y - top) / (photoHeight * scale))

    companion object {
        fun of(photoWidth: Int, photoHeight: Int, stageWidth: Float, stageHeight: Float): PhotoFit {
            val scale = min(stageWidth / photoWidth, stageHeight / photoHeight)
            return PhotoFit((stageWidth - photoWidth * scale) * HALF, (stageHeight - photoHeight * scale) * HALF, scale)
        }
    }
}

private const val START_INSET = 0.06f
private const val CORNER_COUNT = 4
private const val MIN_SHARE_OF_PHOTO = 0.01f
private const val HALF = 0.5f

/** Where the page's corners lie, going round from the top left: as shares of the photo, unless [inPixels] made them pixels. */
data class PageCorners(
    val topLeft: PagePoint,
    val topRight: PagePoint,
    val bottomRight: PagePoint,
    val bottomLeft: PagePoint,
) {
    val points: List<PagePoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** A usable page turns the same way at all four corners, so it is neither crossed nor dented, and is no sliver. */
    fun problem(): CornersProblem? {
        val all = points
        val turns = all.indices.map { i ->
            turn(all[i], all[(i + 1) % CORNER_COUNT], all[(i + 2) % CORNER_COUNT]).sign
        }
        return when {
            turns.any { it == 0f || it != turns[0] } -> CornersProblem.CROSSED
            area(all) < MIN_SHARE_OF_PHOTO -> CornersProblem.TOO_SMALL
            else -> null
        }
    }

    fun moved(index: Int, to: PagePoint): PageCorners {
        val inside = PagePoint(to.x.coerceIn(0f, 1f), to.y.coerceIn(0f, 1f))
        return when (index) {
            0 -> copy(topLeft = inside)
            1 -> copy(topRight = inside)
            2 -> copy(bottomRight = inside)
            else -> copy(bottomLeft = inside)
        }
    }

    fun inPixels(width: Int, height: Int): PageCorners {
        fun scale(point: PagePoint) = PagePoint(point.x * width, point.y * height)
        return PageCorners(scale(topLeft), scale(topRight), scale(bottomRight), scale(bottomLeft))
    }

    /** The index of the corner nearest to [point], if one is within [reach]. */
    fun nearest(point: PagePoint, reach: Float): Int? {
        val distances = points.map { hypot(it.x - point.x, it.y - point.y) }
        val closest = distances.indices.minBy { distances[it] }
        return closest.takeIf { distances[it] <= reach }
    }

    companion object {
        val START = PageCorners(
            PagePoint(START_INSET, START_INSET),
            PagePoint(1 - START_INSET, START_INSET),
            PagePoint(1 - START_INSET, 1 - START_INSET),
            PagePoint(START_INSET, 1 - START_INSET),
        )

        private fun turn(a: PagePoint, b: PagePoint, c: PagePoint): Float =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

        private fun area(points: List<PagePoint>): Float {
            var twice = 0f
            points.indices.forEach { i ->
                val a = points[i]
                val b = points[(i + 1) % CORNER_COUNT]
                twice += a.x * b.y - b.x * a.y
            }
            return kotlin.math.abs(twice) * HALF
        }
    }
}
