package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val FIND_LONG_SIDE = 480

/** In less than this there is no room for a page, nor for a middle to start looking from. */
private const val MIN_SIDE = 16

/** How strong an edge must be to stop the search for the paper, from the most forgiving: a lower one means a tighter fence. */
private val BARRIERS = floatArrayOf(40f, 26f, 16f, 10f, 6f)
private const val MAX_RIM_SHARE = 0.04f
private const val MAX_SHARE = 0.95f
private const val MIN_SHARE = 0.02f

/** The paper may fill little of its outline: a shadow or a hand keeps part of it from being seen as paper. */
private const val MIN_FILLED = 0.55f
private const val EDGE_SAMPLES = 40
private const val FIRST_SAMPLE = 0.05
private const val SAMPLED_SPAN = 0.9
private const val EDGE_LOOK_AROUND = 2
private const val REAL_EDGE = 6f
private const val MIN_SIDE_BACKED = 0.2f
private const val MIN_ALL_BACKED = 0.55f

/**
 * Looks for a sheet of paper on [photo]. Grows the paper out from the middle of the photo, up to the first
 * edges strong enough to stop it, takes the four sides of what it reached, and accepts them only if the
 * photo really has an edge along them. Null when it finds nothing it can trust, which is better than a
 * wrong outline: the user then sets the corners by hand.
 */
fun findPage(photo: PagePixels): PageCorners? {
    val small = SmallPhoto.of(photo, FIND_LONG_SIDE)
    if (min(small.width, small.height) < MIN_SIDE) return null
    val paper = small.paper()
    for (barrier in BARRIERS) {
        val region = small.region(barrier, paper) ?: continue
        if (region.borderShare > MAX_RIM_SHARE || region.share > MAX_SHARE || region.share < MIN_SHARE) continue
        val sides = fitSides(region.boundary()) ?: continue
        val corners = sides.map { it.from }
        if (region.count / areaOf(corners).coerceAtLeast(1f) < MIN_FILLED) continue
        val backed = sides.map { backedByEdges(it, small) }
        if (backed.min() < MIN_SIDE_BACKED || backed.sum() / backed.size < MIN_ALL_BACKED) continue
        val found = cornersOf(sides, small)
        if (found.problem() == null) return found
    }
    return null
}

/** The share of a side's length along which the photo has an edge. */
private fun backedByEdges(side: PageSide, small: SmallPhoto): Float {
    val hits = (0 until EDGE_SAMPLES).count { step ->
        val (x, y) = side.at(FIRST_SAMPLE + SAMPLED_SPAN * step / (EDGE_SAMPLES - 1))
        edgeNear(x.roundToInt(), y.roundToInt(), small)
    }
    return hits / EDGE_SAMPLES.toFloat()
}

private fun edgeNear(x: Int, y: Int, small: SmallPhoto): Boolean {
    if (x !in 0 until small.width || y !in 0 until small.height) return false
    for (row in max(0, y - EDGE_LOOK_AROUND)..min(small.height - 1, y + EDGE_LOOK_AROUND)) {
        for (column in max(0, x - EDGE_LOOK_AROUND)..min(small.width - 1, x + EDGE_LOOK_AROUND)) {
            if (small.edges[row * small.width + column] >= REAL_EDGE) return true
        }
    }
    return false
}

private fun cornersOf(sides: List<PageSide>, small: SmallPhoto): PageCorners {
    fun share(point: PagePoint) = PagePoint(
        (point.x / small.width).coerceIn(0f, 1f),
        (point.y / small.height).coerceIn(0f, 1f),
    )
    val bends = sides.map { side -> side.bend().let { PagePoint(it.x / small.width, it.y / small.height) } }
    return PageCorners(share(sides[0].from), share(sides[1].from), share(sides[2].from), share(sides[3].from), bends)
}
