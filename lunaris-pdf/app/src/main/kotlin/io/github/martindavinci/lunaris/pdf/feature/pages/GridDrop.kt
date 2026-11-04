package io.github.martindavinci.lunaris.pdf.feature.pages

/** A page on screen. [index] is its place in the list of pages. */
internal data class GridCell(val index: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Where a dragged group would land. */
internal object GridDrop {
    /**
     * The place in the list for a finger at ([x], [y]): before the cell it is in, or after it, past its middle.
     * Above or below every cell it goes to the nearest row. Null when no page is on screen.
     */
    fun placeAt(cells: List<GridCell>, x: Float, y: Float): Int? {
        if (cells.isEmpty()) return null
        val nearest = cells.minBy { rowDistance(it, y) }
        val row = cells.filter { it.top == nearest.top }
        val under = row.firstOrNull { x <= (it.left + it.right) / 2 }
        return under?.index ?: (row.maxOf { it.index } + 1)
    }

    private fun rowDistance(cell: GridCell, y: Float): Float = when {
        y < cell.top -> cell.top - y
        y > cell.bottom -> y - cell.bottom
        else -> 0f
    }
}
