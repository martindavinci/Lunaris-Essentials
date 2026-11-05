package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

data class PagePoint(val x: Float, val y: Float)

enum class CornersProblem { CROSSED, EDGE_CROSSED, TOO_SMALL }

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
private const val OUTLINE_STEPS = 12

/** Points in a line, seen through rounding: smaller turns than this say nothing about which side a point is on. */
private const val NO_TURN = 1e-6f
private val STRAIGHT_EDGES = List(CORNER_COUNT) { PagePoint(0f, 0f) }

/** Where each edge's middle is on the flat page, from the top edge going round. */
private val EDGE_MIDDLES = listOf(0.5 to 0.0, 1.0 to 0.5, 0.5 to 1.0, 0.0 to 0.5)

/**
 * Where the page's corners lie, going round from the top left: as shares of the photo, unless [inPixels]
 * made them pixels. [bends] says how far the middle of each edge lies from where a straight edge would
 * have it, from the top edge going round: a sheet is not always flat.
 */
data class PageCorners(
    val topLeft: PagePoint,
    val topRight: PagePoint,
    val bottomRight: PagePoint,
    val bottomLeft: PagePoint,
    val bends: List<PagePoint> = STRAIGHT_EDGES,
) {
    val points: List<PagePoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** What can be dragged: the four corners, then the middle of each edge from the top one going round. */
    val handles: List<PagePoint>
        get() = points + bends.mapIndexed { edge, bend ->
            val middle = straightMiddle(edge)
            PagePoint(middle.x + bend.x, middle.y + bend.y)
        }

    /** Turning the same way at all four corners, so neither crossed nor dented. */
    internal val convex: Boolean
        get() {
            val all = points
            val turns = all.indices.map { i ->
                turn(all[i], all[(i + 1) % CORNER_COUNT], all[(i + 2) % CORNER_COUNT]).sign
            }
            return turns.none { it == 0f || it != turns[0] }
        }

    /** A usable page is convex at its corners, has edges that do not run through each other, and is no sliver. */
    fun problem(): CornersProblem? = when {
        !convex -> CornersProblem.CROSSED
        bends != STRAIGHT_EDGES && crossesItself(outline(OUTLINE_STEPS)) -> CornersProblem.EDGE_CROSSED
        area(points) < MIN_SHARE_OF_PHOTO -> CornersProblem.TOO_SMALL
        else -> null
    }

    /** Moves one of the [handles]. An edge keeps its bend when its corners move. */
    fun moved(index: Int, to: PagePoint): PageCorners {
        val inside = PagePoint(to.x.coerceIn(0f, 1f), to.y.coerceIn(0f, 1f))
        return when (index) {
            0 -> copy(topLeft = inside)
            1 -> copy(topRight = inside)
            2 -> copy(bottomRight = inside)
            3 -> copy(bottomLeft = inside)
            else -> {
                val edge = index - CORNER_COUNT
                val middle = straightMiddle(edge)
                val bend = PagePoint(inside.x - middle.x, inside.y - middle.y)
                copy(bends = bends.mapIndexed { other, old -> if (other == edge) bend else old })
            }
        }
    }

    fun inPixels(width: Int, height: Int): PageCorners {
        fun scale(point: PagePoint) = PagePoint(point.x * width, point.y * height)
        return PageCorners(scale(topLeft), scale(topRight), scale(bottomRight), scale(bottomLeft), bends.map(::scale))
    }

    /** The index of the one of the [handles] nearest to [point], if one is within [reach]. While crossed there are no edges to bend. */
    fun nearest(point: PagePoint, reach: Float): Int? {
        val distances = (if (convex) handles else points).map { hypot(it.x - point.x, it.y - point.y) }
        val closest = distances.indices.minBy { distances[it] }
        return closest.takeIf { distances[it] <= reach }
    }

    /** The middle of an edge as a flat page in perspective has it, which is off the plain middle towards the far end. */
    private fun straightMiddle(edge: Int): PagePoint {
        if (!convex) {
            val a = points[edge]
            val b = points[(edge + 1) % CORNER_COUNT]
            return PagePoint((a.x + b.x) * HALF, (a.y + b.y) * HALF)
        }
        val mapping = PerspectiveMapping.between(1, 1, this)
        val (u, v) = EDGE_MIDDLES[edge]
        return PagePoint(mapping.x(u, v).toFloat(), mapping.y(u, v).toFloat())
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

        private fun crossesItself(path: List<PagePoint>): Boolean = path.indices.any { i ->
            // Two neighbours share a point: that is no crossing.
            (i + 2 until path.size).any { j ->
                (i != 0 || j != path.size - 1) &&
                    segmentsCross(path[i], path[(i + 1) % path.size], path[j], path[(j + 1) % path.size])
            }
        }

        private fun segmentsCross(a: PagePoint, b: PagePoint, c: PagePoint, d: PagePoint): Boolean =
            onOppositeSides(turn(a, b, c), turn(a, b, d)) && onOppositeSides(turn(c, d, a), turn(c, d, b))

        private fun onOppositeSides(one: Float, other: Float): Boolean =
            (one > NO_TURN && other < -NO_TURN) || (one < -NO_TURN && other > NO_TURN)

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
