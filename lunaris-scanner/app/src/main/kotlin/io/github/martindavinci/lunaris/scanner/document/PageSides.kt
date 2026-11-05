package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private const val CORNERS = 4
private const val ROUNDS = 3
private const val TRIM = 0.08f
private const val NEAR_THE_SIDE = 0.08f
private const val MIN_NEAR_THE_SIDE = 6f
private const val LINE = 1
private const val BOW = 2

/** A bow must fit clearly better than a line, and be large enough to matter. */
private const val BETTER_FIT = 0.12
private const val SMALLEST_BOW = 0.012
private const val SEARCH_STEPS = 40
private const val SEARCH_REACH = 0.15
private const val AREA_STEP = 1e-3f

/** One side of the page: [curve] is how far off the straight line from [from] to [to] it runs, as a polynomial of the share along it. */
internal class PageSide(val from: PagePoint, val to: PagePoint, val curve: DoubleArray?, val bowed: Boolean) {
    private val length = hypot(to.x - from.x, to.y - from.y).coerceAtLeast(1f)

    fun at(along: Double): Pair<Double, Double> {
        val off = curve?.let { value(it, along) } ?: 0.0
        return from.x + (to.x - from.x) * along - (to.y - from.y) / length * off to
            from.y + (to.y - from.y) * along + (to.x - from.x) / length * off
    }

    /** How far the middle lies off the straight line between the ends, as an offset on the photo. */
    fun bend(): PagePoint {
        val curve = curve
        if (!bowed || curve == null) return PagePoint(0f, 0f)
        val middle = sag(curve)
        return PagePoint((-(to.y - from.y) / length * middle).toFloat(), ((to.x - from.x) / length * middle).toFloat())
    }
}

/** The four sides of the shape outlined by [points], going clockwise from the top left. Null when there is no such shape. */
internal fun fitSides(points: List<PagePoint>): List<PageSide>? {
    val hull = convexHull(points)
    if (hull.size < CORNERS) return null
    var corners = inOrder(largestQuad(hull))
    var sides = emptyList<PageSide>()
    repeat(ROUNDS) {
        sides = corners.indices.map { fitSide(points, corners[it], corners[(it + 1) % CORNERS]) }
        corners = corners.indices.map { cornerBetween(sides[(it + CORNERS - 1) % CORNERS], sides[it], corners[it]) }
    }
    return sides.indices.map { PageSide(corners[it], corners[(it + 1) % CORNERS], refitted(sides[it], corners[it], corners[(it + 1) % CORNERS]), sides[it].bowed) }
}

/** The curve of a side, measured again between the corners it ended up with. */
private fun refitted(side: PageSide, from: PagePoint, to: PagePoint): DoubleArray? {
    if (side.curve == null) return null
    val length = hypot(to.x - from.x, to.y - from.y).coerceAtLeast(1f).toDouble()
    val rows = (0..SEARCH_STEPS).map { step ->
        val (x, y) = side.at(step.toDouble() / SEARCH_STEPS)
        val along = ((x - from.x) * (to.x - from.x) + (y - from.y) * (to.y - from.y)) / (length * length)
        val off = (-(x - from.x) * (to.y - from.y) + (y - from.y) * (to.x - from.x)) / length
        along to off
    }
    return polynomial(rows.map { it.first }, rows.map { it.second }, if (side.bowed) BOW else LINE)
}

private fun fitSide(points: List<PagePoint>, from: PagePoint, to: PagePoint): PageSide {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val reach = max(MIN_NEAR_THE_SIDE, NEAR_THE_SIDE * length)
    val along = ArrayList<Double>()
    val off = ArrayList<Double>()
    points.forEach { p ->
        val a = ((p.x - from.x) * dx + (p.y - from.y) * dy) / length
        val o = (-(p.x - from.x) * dy + (p.y - from.y) * dx) / length
        if (a > TRIM * length && a < (1 - TRIM) * length && abs(o) < reach) {
            along += (a / length).toDouble()
            off += o.toDouble()
        }
    }
    val line = robustFit(along, off, LINE)
    val bow = robustFit(along, off, BOW)
    val bowed = line != null && bow != null && bow.second > line.second + BETTER_FIT && abs(sag(bow.first)) > SMALLEST_BOW * length
    return PageSide(from, to, if (bowed) bow!!.first else line?.first, bowed)
}

/** Where the end of one side comes closest to the start of the next. */
private fun cornerBetween(before: PageSide, after: PageSide, start: PagePoint): PagePoint {
    var best = start
    var bestDistance = Double.MAX_VALUE
    for (i in 0..SEARCH_STEPS) {
        val (ax, ay) = before.at(1 - SEARCH_REACH + i * 2 * SEARCH_REACH / SEARCH_STEPS)
        for (j in 0..SEARCH_STEPS) {
            val (bx, by) = after.at(-SEARCH_REACH + j * 2 * SEARCH_REACH / SEARCH_STEPS)
            val distance = (ax - bx) * (ax - bx) + (ay - by) * (ay - by)
            if (distance < bestDistance) {
                bestDistance = distance
                best = PagePoint(((ax + bx) / 2).toFloat(), ((ay + by) / 2).toFloat())
            }
        }
    }
    return best
}

/** Andrew's monotone chain. */
internal fun convexHull(points: List<PagePoint>): List<PagePoint> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    fun half(sequence: List<PagePoint>): List<PagePoint> {
        val out = ArrayList<PagePoint>()
        sequence.forEach { p ->
            while (out.size >= 2 && cross(out[out.size - 2], out[out.size - 1], p) <= 0) out.removeAt(out.size - 1)
            out += p
        }
        return out.dropLast(1)
    }
    return half(sorted) + half(sorted.asReversed())
}

private fun cross(a: PagePoint, b: PagePoint, c: PagePoint) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

internal fun areaOf(points: List<PagePoint>): Float {
    var twice = 0f
    points.indices.forEach { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        twice += a.x * b.y - b.x * a.y
    }
    return abs(twice) / 2
}

/** The four hull points enclosing the most, improved one at a time from four spread ones. */
private fun largestQuad(hull: List<PagePoint>): List<PagePoint> {
    val n = hull.size
    val picks = intArrayOf(0, n / CORNERS, n / 2, n * (CORNERS - 1) / CORNERS)
    var improved = true
    while (improved) {
        improved = false
        for (k in 0 until CORNERS) {
            var bestArea = areaOf(picks.map { hull[it] })
            var bestIndex = picks[k]
            val stop = picks[(k + 1) % CORNERS]
            var i = (picks[(k + CORNERS - 1) % CORNERS] + 1) % n
            while (i != stop) {
                val candidate = picks.copyOf().also { it[k] = i }
                val area = areaOf(candidate.map { hull[it] })
                if (area > bestArea + AREA_STEP) {
                    bestArea = area
                    bestIndex = i
                }
                i = (i + 1) % n
            }
            if (bestIndex != picks[k]) {
                picks[k] = bestIndex
                improved = true
            }
        }
    }
    return picks.map { hull[it] }
}

/** The top left first, then clockwise as seen on the screen. */
private fun inOrder(quad: List<PagePoint>): List<PagePoint> {
    val cx = quad.sumOf { it.x.toDouble() } / CORNERS
    val cy = quad.sumOf { it.y.toDouble() } / CORNERS
    val round = quad.sortedBy { atan2(it.y - cy, it.x - cx) }
    val first = round.indices.minBy { round[it].x + round[it].y }
    return round.drop(first) + round.take(first)
}
