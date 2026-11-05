package io.github.martindavinci.lunaris.scanner.document

/** A parabola that is 0 at both ends of an edge and 1 in the middle: 4 t (1 - t). */
private const val BOW = 4.0

/**
 * Where each point of a flat page of [width] by [height] lies on the photo: the perspective of the four
 * corners, moved by how much the edges bend. Each edge bends as a parabola, and the inside of the page
 * follows the two edges facing it in proportion to how near it is to each, as a sheet curled along one
 * direction does. With straight edges it is the perspective alone.
 */
internal class PageSurface(private val width: Int, private val height: Int, quad: PageCorners) {
    private val mapping = PerspectiveMapping.between(width, height, quad)
    private val top = quad.bends[0]
    private val right = quad.bends[1]
    private val bottom = quad.bends[2]
    private val left = quad.bends[3]

    fun x(u: Double, v: Double): Double = mapping.x(u, v) + moved(u / width, v / height, top.x, right.x, bottom.x, left.x)

    fun y(u: Double, v: Double): Double = mapping.y(u, v) + moved(u / width, v / height, top.y, right.y, bottom.y, left.y)

    private fun moved(across: Double, down: Double, top: Float, right: Float, bottom: Float, left: Float): Double {
        val bowAcross = BOW * across * (1 - across)
        val bowDown = BOW * down * (1 - down)
        return bowAcross * ((1 - down) * top + down * bottom) + bowDown * ((1 - across) * left + across * right)
    }
}

/** The page's edge going round from the top left, as a path to draw. While the corners are crossed there is no surface, only the corners. */
internal fun PageCorners.outline(stepsPerEdge: Int): List<PagePoint> {
    if (!convex) return points
    val surface = PageSurface(1, 1, this)
    fun at(u: Double, v: Double) = PagePoint(surface.x(u, v).toFloat(), surface.y(u, v).toFloat())
    val shares = List(stepsPerEdge) { it.toDouble() / stepsPerEdge }
    return shares.map { at(it, 0.0) } + shares.map { at(1.0, it) } + shares.map { at(1 - it, 1.0) } + shares.map { at(0.0, 1 - it) }
}
