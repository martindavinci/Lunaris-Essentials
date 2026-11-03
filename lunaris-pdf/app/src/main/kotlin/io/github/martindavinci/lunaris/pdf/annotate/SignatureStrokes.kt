package io.github.martindavinci.lunaris.pdf.annotate

data class StrokePoint(val x: Float, val y: Float)

data class StrokeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

object SignatureStrokes {
    /** The area a signature takes, to crop its picture. Null when nothing was drawn. */
    fun bounds(strokes: List<List<StrokePoint>>, margin: Float): StrokeBounds? {
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        return StrokeBounds(
            left = points.minOf { it.x } - margin,
            top = points.minOf { it.y } - margin,
            right = points.maxOf { it.x } + margin,
            bottom = points.maxOf { it.y } + margin,
        )
    }
}
