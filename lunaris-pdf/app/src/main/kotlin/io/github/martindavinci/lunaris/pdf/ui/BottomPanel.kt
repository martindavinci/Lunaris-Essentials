package io.github.martindavinci.lunaris.pdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val SCRIM_ALPHA = 0.55f
private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val HandleAreaHeight = 28.dp
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private val HandleShape = RoundedCornerShape(2.dp)
private val DismissDistance = 96.dp

/**
 * A panel over a scrim, closed by back, a tap outside or dragging its handle down. Material's bottom
 * sheet is still an experimental API.
 */
@Composable
internal fun BottomPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var dragged by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(androidx.compose.ui.platform.LocalDensity.current) { DismissDistance.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .statusBarsPadding(),
    ) {
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, dragged.roundToInt()) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HandleAreaHeight)
                        .draggable(
                            state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                if (dragged > dismissPx) onDismiss() else dragged = 0f
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(HandleWidth, HandleHeight)
                            .background(MaterialTheme.colorScheme.outline, HandleShape),
                    )
                }
                content()
            }
        }
    }
}
