package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import kotlin.math.roundToInt

internal const val HEADER_KEY = "header"
private val GridGap = 12.dp
private val LineWidth = 3.dp

@Composable
internal fun SlotTile(
    slot: PageSlot,
    number: Int,
    selected: Boolean,
    selecting: Boolean,
    dimmed: Boolean,
    thumbnail: suspend (PageSlot) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    showNumber: Boolean = true,
) {
    val blank = (slot.content as? SlotContent.Blank)?.let { it.width / it.height }
    PageTile(
        number = number,
        turn = slot.rotation,
        blankAspect = blank,
        selected = selected,
        selecting = selecting,
        dimmed = dimmed,
        key = slot.content,
        load = { thumbnail(slot) },
        modifier = modifier,
        showNumber = showNumber,
    )
}

@Composable
internal fun InsertionLine(grid: LazyGridState, place: Int?) {
    place ?: return
    val color = MaterialTheme.colorScheme.primary
    val cells = grid.cells()
    val before = cells.firstOrNull { it.index == place }
    val after = cells.firstOrNull { it.index == place - 1 }
    val density = LocalDensity.current
    val half = with(density) { GridGap.toPx() / 2 }
    val x = before?.let { it.left - half } ?: after?.let { it.right + half } ?: return
    val top = before?.top ?: after?.top ?: return
    val height = (before?.bottom ?: after?.bottom ?: return) - top
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt() - LineWidth.roundToPx() / 2, top.roundToInt()) }
            .size(LineWidth, with(density) { height.toDp() })
            .background(color, RoundedCornerShape(LineWidth)),
    )
}

/** Pages on screen, in the grid's own space. */
internal fun LazyGridState.cells(): List<GridCell> = layoutInfo.visibleItemsInfo
    .filter { it.key != HEADER_KEY }
    .map { item ->
        // The header is item 0 of the grid, but no page.
        GridCell(
            index = item.index - 1,
            left = item.offset.x.toFloat(),
            top = item.offset.y.toFloat(),
            right = (item.offset.x + item.size.width).toFloat(),
            bottom = (item.offset.y + item.size.height).toFloat(),
        )
    }

internal fun LazyGridState.cellAt(point: Offset): Int? = cells().firstOrNull {
    point.x in it.left..it.right && point.y in it.top..it.bottom
}?.index
