package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.max
import kotlin.math.min

private const val ANY = 0.001f
private const val ALL = 0.999f

/** The indices of the middle half of a picture, each way. */
internal fun middleOf(width: Int, height: Int): List<Int> =
    (height / 4 until height * 3 / 4).flatMap { row -> (width / 4 until width * 3 / 4).map { row * width + it } }

/** The mean over a square of the given radius around each point. Past the sides of the picture, its last pixels repeat. */
internal fun boxMean(source: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
    val across = FloatArray(source.size)
    for (y in 0 until height) {
        var sum = 0f
        for (x in -radius..radius) sum += source[y * width + x.coerceIn(0, width - 1)]
        for (x in 0 until width) {
            across[y * width + x] = sum
            sum += source[y * width + min(x + radius + 1, width - 1)] - source[y * width + max(x - radius, 0)]
        }
    }
    val out = FloatArray(source.size)
    val cells = (2 * radius + 1) * (2 * radius + 1).toFloat()
    for (x in 0 until width) {
        var sum = 0f
        for (y in -radius..radius) sum += across[y.coerceIn(0, height - 1) * width + x]
        for (y in 0 until height) {
            out[y * width + x] = sum / cells
            sum += across[min(y + radius + 1, height - 1) * width + x] - across[max(y - radius, 0) * width + x]
        }
    }
    return out
}

/** A yes or no for each point of a small photo. */
internal class Mask(val cells: BooleanArray, val width: Int, val height: Int) {
    val share: Float get() = count / cells.size.toFloat()
    val count: Int get() = cells.count { it }

    /** How much of the picture's outer rim is covered: a page lies inside it, and a leak out to the table reaches it. */
    val borderShare: Float get() = rim().let { rim -> rim.count { cells[it] } / rim.size.toFloat() }

    fun dilated(radius: Int): Mask = spread(radius).let { mean -> Mask(BooleanArray(cells.size) { mean[it] > ANY }, width, height) }

    fun eroded(radius: Int): Mask = spread(radius).let { mean -> Mask(BooleanArray(cells.size) { mean[it] > ALL }, width, height) }

    /** Everything that the outside of the picture cannot reach is inside the shape. */
    fun filled(): Mask {
        val open = BooleanArray(cells.size) { !cells[it] }
        val outside = reach(open, rim().filter { open[it] })
        return Mask(BooleanArray(cells.size) { !outside[it] }, width, height)
    }

    /** The largest connected part that touches the middle of the picture. */
    fun biggestNearCentre(): Mask? {
        val seen = BooleanArray(cells.size)
        val queue = IntArray(cells.size)
        var tail = 0
        var bestStart = 0
        var bestEnd = 0
        for (index in middleOf(width, height)) {
            if (!cells[index] || seen[index]) continue
            val start = tail
            seen[index] = true
            queue[tail++] = index
            tail = flood(cells, seen, queue, start, tail)
            if (tail - start > bestEnd - bestStart) {
                bestStart = start
                bestEnd = tail
            }
        }
        if (bestEnd == bestStart) return null
        val best = BooleanArray(cells.size)
        for (i in bestStart until bestEnd) best[queue[i]] = true
        return Mask(best, width, height)
    }

    /** The points of the shape that lie on its edge. */
    fun boundary(): List<PagePoint> {
        val inner = eroded(1).cells
        return cells.indices.filter { cells[it] && !inner[it] }.map { PagePoint((it % width).toFloat(), (it / width).toFloat()) }
    }

    private fun rim(): List<Int> = (0 until width).flatMap { listOf(it, (height - 1) * width + it) } +
        (0 until height).flatMap { listOf(it * width, it * width + width - 1) }

    private fun spread(radius: Int): FloatArray =
        boxMean(FloatArray(cells.size) { if (cells[it]) 1f else 0f }, width, height, radius)

    private fun reach(open: BooleanArray, from: List<Int>): BooleanArray {
        val seen = BooleanArray(open.size)
        val queue = IntArray(open.size)
        var tail = 0
        from.forEach { start ->
            if (open[start] && !seen[start]) {
                seen[start] = true
                queue[tail++] = start
            }
        }
        flood(open, seen, queue, 0, tail)
        return seen
    }

    /** Goes on from what the queue holds between [start] and [end], 4-connected, and returns the new end of the queue. */
    private fun flood(open: BooleanArray, seen: BooleanArray, queue: IntArray, start: Int, end: Int): Int {
        var head = start
        var tail = end
        while (head < tail) {
            val at = queue[head++]
            val x = at % width
            fun visit(next: Int) {
                if (open[next] && !seen[next]) {
                    seen[next] = true
                    queue[tail++] = next
                }
            }
            if (x > 0) visit(at - 1)
            if (x < width - 1) visit(at + 1)
            if (at >= width) visit(at - width)
            if (at < open.size - width) visit(at + width)
        }
        return tail
    }
}
