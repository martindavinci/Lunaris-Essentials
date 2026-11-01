package tools.lunaris.texteditor.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ListGap = 4.dp
private val EdgeMargin = 8.dp
/** Below this there's no room for even one row, so the hints stay hidden. */
private val MinListHeight = 48.dp

/** Focus, dismissal and position of the field the recent-search hints hang from. */
@Stable
class SearchHintsState {
    internal var focused by mutableStateOf(false)
    internal var hidden by mutableStateOf(false)
    internal var anchor by mutableStateOf(Rect.Zero)

    /** Hides the hints until the query text changes or the field gains focus again. */
    fun hide() { hidden = true }

    fun onTextChanged() { hidden = false }
}

@Composable
fun rememberSearchHintsState() = remember { SearchHintsState() }

/** Marks the query field: the hints show while it has focus and hang just below it. */
fun Modifier.searchHintsAnchor(state: SearchHintsState): Modifier = this
    .onGloballyPositioned { state.anchor = it.boundsInRoot() }
    .onFocusChanged {
        if (it.isFocused && !state.focused) state.hidden = false
        state.focused = it.isFocused
    }

/**
 * Draws [content] and floats the file's recent queries matching [query] under the anchored field.
 * The list overlays the content without moving it and is clamped to this box — it scrolls rather
 * than spill past the dialog, so it can never cover the keyboard. A tap anywhere else hides it.
 */
@Composable
fun SearchHintsBox(
    state: SearchHintsState,
    history: List<String>,
    query: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var host by remember { mutableStateOf(Rect.Zero) }
    var list by remember { mutableStateOf(Rect.Zero) }
    val hints = remember(history, query) { searchHints(history, query) }
    val density = LocalDensity.current
    val gap = with(density) { ListGap.toPx() }
    val room = host.bottom - state.anchor.bottom - gap - with(density) { EdgeMargin.toPx() }
    val visible = state.focused && !state.hidden && hints.isNotEmpty() &&
        room >= with(density) { MinListHeight.toPx() }

    BackHandler(visible) { state.hide() }
    Box(
        modifier
            .onGloballyPositioned { host = it.boundsInRoot() }
            .pointerInput(state) {
                // Observe only (Initial pass, nothing consumed): the tap still reaches its target.
                awaitEachGesture {
                    val at = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        .position + host.topLeft
                    if (!list.contains(at) && !state.anchor.contains(at)) state.hide()
                }
            },
    ) {
        content()
        if (visible) {
            Surface(
                shape = MenuDefaults.shape,
                color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (state.anchor.left - host.left).roundToInt(),
                            (state.anchor.bottom - host.top + gap).roundToInt(),
                        )
                    }
                    .width(with(density) { state.anchor.width.toDp() })
                    .heightIn(max = with(density) { room.toDp() })
                    .onGloballyPositioned { list = it.boundsInRoot() },
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    hints.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = { onPick(q); state.hide() },
                        )
                    }
                }
            }
        }
    }
}

/** Recents containing [query] (case-insensitive), minus the query itself; all of them when it's empty. */
internal fun searchHints(history: List<String>, query: String): List<String> =
    history.filter { it != query && it.contains(query, ignoreCase = true) }
