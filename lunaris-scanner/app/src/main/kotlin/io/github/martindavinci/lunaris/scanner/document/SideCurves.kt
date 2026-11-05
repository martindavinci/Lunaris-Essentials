package io.github.martindavinci.lunaris.scanner.document

import kotlin.math.abs
import kotlin.random.Random

private const val AGREES_WITHIN = 1.2
private const val TRIES = 150
private const val SEED = 7
private const val SINGULAR = 1e-9

/** A polynomial's middle, compared with the straight line between its two ends. */
internal fun sag(curve: DoubleArray): Double = value(curve, 0.5) - (value(curve, 0.0) + value(curve, 1.0)) / 2

internal fun value(curve: DoubleArray, along: Double): Double = curve.foldRight(0.0) { c, sum -> sum * along + c }

/** Tries curves through a few random points, keeps the one most points agree with and fits those. Also returns the share that agrees. */
internal fun robustFit(along: List<Double>, off: List<Double>, degree: Int): Pair<DoubleArray, Double>? {
    if (along.size < degree + 2) return null
    val random = Random(SEED)
    var best: BooleanArray? = null
    var bestCount = -1
    repeat(TRIES) {
        val picks = List(degree + 1) { random.nextInt(along.size) }
        val curve = polynomial(picks.map { along[it] }, picks.map { off[it] }, degree) ?: return@repeat
        val agrees = BooleanArray(along.size) { abs(value(curve, along[it]) - off[it]) < AGREES_WITHIN }
        val count = agrees.count { it }
        if (count > bestCount) {
            best = agrees
            bestCount = count
        }
    }
    val chosen = best ?: return null
    val curve = polynomial(along.filterIndexed { i, _ -> chosen[i] }, off.filterIndexed { i, _ -> chosen[i] }, degree) ?: return null
    val share = along.indices.count { abs(value(curve, along[it]) - off[it]) < AGREES_WITHIN } / along.size.toDouble()
    return curve to share
}

/** Least squares through the normal equations; null when the points cannot settle the curve. */
internal fun polynomial(xs: List<Double>, ys: List<Double>, degree: Int): DoubleArray? {
    val size = degree + 1
    val matrix = Array(size) { DoubleArray(size + 1) }
    xs.indices.forEach { index ->
        val powers = DoubleArray(size * 2 - 1).also { it[0] = 1.0 }
        for (p in 1 until powers.size) powers[p] = powers[p - 1] * xs[index]
        for (row in 0 until size) {
            for (column in 0 until size) matrix[row][column] += powers[row + column]
            matrix[row][size] += powers[row] * ys[index]
        }
    }
    for (column in 0 until size) {
        val pivot = (column until size).maxBy { abs(matrix[it][column]) }
        if (abs(matrix[pivot][column]) < SINGULAR) return null
        matrix[column] = matrix[pivot].also { matrix[pivot] = matrix[column] }
        for (row in column + 1 until size) {
            val factor = matrix[row][column] / matrix[column][column]
            for (k in column..size) matrix[row][k] -= factor * matrix[column][k]
        }
    }
    val out = DoubleArray(size)
    for (row in size - 1 downTo 0) {
        var rest = matrix[row][size]
        for (k in row + 1 until size) rest -= matrix[row][k] * out[k]
        out[row] = rest / matrix[row][row]
    }
    return out
}
