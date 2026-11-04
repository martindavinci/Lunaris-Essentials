package io.github.martindavinci.lunaris.pdf.feature.pages

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val CellMinWidth = 96.dp
private val GridPadding = 16.dp
private val GridGap = 12.dp
private val AutoScrollZone = 72.dp
private val AutoScrollPerFrame = 12.dp
private val StackWidth = 96.dp

/** A page being carried: [ids] go together, [place] is where they would land, [finger] is in the grid's own space. */
internal data class PageDrag(val ids: Set<Long>, val shown: PageSlot, val finger: Offset, val place: Int?)

/**
 * All the pages, in one grid: a tap selects, a long press carries a page, or all those selected if it is one
 * of them. The places they leave stay marked until they land. Near the edges the grid scrolls by itself.
 */
@Composable
internal fun PagesGrid(
    state: PagesUiState,
    thumbnail: suspend (PageSlot) -> ImageBitmap?,
    onAction: (PagesAction) -> Unit,
    onDrag: (PageDrag?) -> Unit,
    header: @Composable () -> Unit,
    bottomSpace: Dp,
) {
    val grid = rememberLazyGridState()
    // The gesture outlives many states of the list.
    val latest by rememberUpdatedState(state)
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var ids by remember { mutableStateOf(emptySet<Long>()) }
    var shown by remember { mutableStateOf<PageSlot?>(null) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var place by remember { mutableStateOf<Int?>(null) }
    val zone = with(density) { AutoScrollZone.toPx() }
    val step = with(density) { AutoScrollPerFrame.toPx() }

    fun aim() {
        place = GridDrop.placeAt(grid.cells(), fingerX, fingerY)
        val carried = shown
        if (carried != null) onDrag(PageDrag(ids, carried, Offset(fingerX, fingerY), place))
    }

    LaunchedEffect(dragging) {
        while (dragging) {
            withFrameNanos { }
            val height = grid.layoutInfo.viewportSize.height
            val speed = when {
                fingerY < zone -> -step
                fingerY > height - zone -> step
                else -> 0f
            }
            if (speed != 0f) {
                grid.scrollBy(speed)
                aim()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(CellMinWidth),
            contentPadding = PaddingValues(start = GridPadding, end = GridPadding, bottom = bottomSpace),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap),
            userScrollEnabled = !dragging,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.canEdit) {
                    if (!state.canEdit) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val index = grid.cellAt(start) ?: return@detectDragGesturesAfterLongPress
                            val page = latest.slots.getOrNull(index) ?: return@detectDragGesturesAfterLongPress
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            ids = if (page.id in latest.selected) latest.selected else setOf(page.id)
                            shown = page
                            fingerX = start.x
                            fingerY = start.y
                            dragging = true
                            aim()
                        },
                        onDrag = { change, amount ->
                            if (dragging) {
                                change.consume()
                                fingerX += amount.x
                                fingerY += amount.y
                                aim()
                            }
                        },
                        onDragEnd = {
                            val landing = place
                            val moved = ids
                            if (dragging && landing != null) onAction(PagesAction.Move(moved, landing))
                            endDrag(scope) {
                                dragging = false
                                shown = null
                                onDrag(null)
                            }
                        },
                        onDragCancel = {
                            endDrag(scope) {
                                dragging = false
                                shown = null
                                onDrag(null)
                            }
                        },
                    )
                },
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            itemsIndexed(state.slots, key = { _, slot -> slot.id }) { index, slot ->
                val label = stringResource(R.string.pages_page_label, index + 1)
                val isSelected = slot.id in state.selected
                val tileModifier = Modifier
                    .clickable(enabled = state.canEdit) {
                        // The finger lifted after carrying a page must not also select it.
                        if (!dragging) onAction(PagesAction.ToggleSelected(slot.id))
                    }
                    .clearAndSetSemantics {
                        contentDescription = label
                        selected = isSelected
                    }
                if (dragging && slot.id in ids) {
                    PageGap(index + 1, tileModifier)
                } else {
                    SlotTile(slot, index + 1, isSelected, state.selecting, !state.canEdit, thumbnail, tileModifier.animateItem())
                }
            }
        }
        if (dragging) {
            val carried = shown
            InsertionLine(grid, place)
            if (carried != null) {
                DragStack(
                    count = ids.size,
                    content = { SlotTile(carried, 0, selected = true, selecting = false, dimmed = false, thumbnail = thumbnail, showNumber = false) },
                    modifier = Modifier
                        .offset { IntOffset(fingerX.roundToInt() - StackWidth.roundToPx() / 2, fingerY.roundToInt() - StackWidth.roundToPx() / 2) }
                        .width(StackWidth),
                )
            }
        }
    }
}

private inline fun endDrag(scope: CoroutineScope, crossinline done: () -> Unit) {
    // After this frame: the tile's own click, if it comes, must still see the drag.
    scope.launch {
        withFrameNanos { }
        done()
    }
}
