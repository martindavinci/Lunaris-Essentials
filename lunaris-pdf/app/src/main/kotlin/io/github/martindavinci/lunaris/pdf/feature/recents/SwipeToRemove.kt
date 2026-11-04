package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import kotlin.math.abs
import kotlin.math.sign

private val RevealPadding = 24.dp
private val RevealSpacing = 12.dp
private val FlingSpeed = 800.dp

internal object SwipeRemoval {
    private const val LINE = 0.4f

    fun passedLine(offset: Float, width: Float): Boolean = width > 0f && abs(offset) >= width * LINE

    /** How far it went, or a quick flick away from its place. A flick back towards it wins over the distance. */
    fun removes(offset: Float, velocity: Float, width: Float, flingSpeed: Float): Boolean {
        if (offset == 0f || width <= 0f) return false
        val flicked = abs(velocity) >= flingSpeed
        val away = velocity.sign == offset.sign
        return if (flicked) away else passedLine(offset, width)
    }
}

/** Slides either way to leave the list. The undo that follows makes any stronger warning unnecessary. */
@Composable
internal fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val flingSpeed = with(LocalDensity.current) { FlingSpeed.toPx() }
    val label = stringResource(R.string.recents_remove)
    val drag = rememberDraggableState { delta ->
        val before = SwipeRemoval.passedLine(offset, width.toFloat())
        offset += delta
        if (before != SwipeRemoval.passedLine(offset, width.toFloat())) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        onRemove()
                        true
                    },
                )
            }
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val removing = SwipeRemoval.removes(offset, velocity, width.toFloat(), flingSpeed)
                    val target = if (removing) sign(offset) * width else 0f
                    animate(offset, target, initialVelocity = velocity) { value, _ -> offset = value }
                    if (removing) onRemove()
                },
            ),
    ) {
        if (offset != 0f) RemovalReveal(label, offset > 0f, Modifier.matchParentSize())
        Box(Modifier.graphicsLayer { translationX = offset }.background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

/** What the slide uncovers, on the side it leaves empty. Neutral: nothing is lost, and the file stays. */
@Composable
private fun RemovalReveal(label: String, fromLeft: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.background(colors.surfaceVariant).padding(horizontal = RevealPadding)) {
        Row(
            modifier = Modifier.align(if (fromLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RevealSpacing),
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = colors.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
