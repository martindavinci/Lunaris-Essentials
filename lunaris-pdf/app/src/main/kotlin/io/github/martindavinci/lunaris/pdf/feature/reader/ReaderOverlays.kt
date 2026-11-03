package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

/** Neutral and translucent, like the scroller it lines up with: the page stays the only colour on screen. */
private val TopButtonColor = Color(0x9E696E76)
private val IndicatorColor = Color(0xD91E2127)
private val TopButtonSize = 44.dp
private val TopButtonElevation = 3.dp
private val TopButtonIconPadding = 10.dp
private val EdgeMargin = 12.dp
private val BarMargin = 16.dp
private val IndicatorTop = 16.dp
private val IndicatorShape = RoundedCornerShape(16.dp)
private val IndicatorPaddingHorizontal = 14.dp
private val IndicatorPaddingVertical = 6.dp
private val JumpHeight = 44.dp
private val JumpShape = RoundedCornerShape(22.dp)
private val JumpPaddingStart = 12.dp
private val JumpPaddingEnd = 16.dp
private val JumpSpacing = 8.dp
private val JumpElevation = 4.dp
private val JumpIconSize = 20.dp
private val OutlineWidth = 1.dp

/** What floats over the page. [bottomSpace] is what the bottom bar covers now. */
@Composable
internal fun ReaderOverlays(state: ReaderUiState, bottomSpace: Dp, onAction: (ReaderAction) -> Unit) {
    val bottom by animateDpAsState(bottomSpace + BarMargin, label = "above the bar")
    Box(Modifier.fillMaxSize()) {
        PageIndicator(state)
        TopButton(state.showTopButton, bottom, onAction)
        JumpChip(state, bottom, onAction)
    }
}

@Composable
private fun BoxScope.PageIndicator(state: ReaderUiState) {
    AnimatedVisibility(
        visible = state.showPageIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = IndicatorTop),
    ) {
        Surface(shape = IndicatorShape, color = IndicatorColor, contentColor = Color.White) {
            Text(
                text = stringResource(R.string.reader_page_short, state.page, state.pages),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = IndicatorPaddingHorizontal, vertical = IndicatorPaddingVertical),
            )
        }
    }
}

@Composable
private fun BoxScope.TopButton(visible: Boolean, bottom: Dp, onAction: (ReaderAction) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = EdgeMargin, bottom = bottom),
    ) {
        Surface(
            onClick = { onAction(ReaderAction.BackToTop) },
            shape = CircleShape,
            color = TopButtonColor,
            contentColor = Color.White,
            shadowElevation = TopButtonElevation,
            modifier = Modifier.size(TopButtonSize),
        ) {
            Icon(
                Icons.Filled.VerticalAlignTop,
                contentDescription = stringResource(R.string.reader_back_to_top),
                modifier = Modifier.padding(TopButtonIconPadding),
            )
        }
    }
}

/** The way back after a long move. No dialog and nothing to learn: it leaves by itself as reading goes on. */
@Composable
private fun BoxScope.JumpChip(state: ReaderUiState, bottom: Dp, onAction: (ReaderAction) -> Unit) {
    val jump = state.jump
    AnimatedVisibility(
        visible = state.showJump,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomStart).padding(start = EdgeMargin, bottom = bottom),
    ) {
        if (jump != null) {
            val colors = MaterialTheme.colorScheme
            Surface(
                onClick = { onAction(ReaderAction.ReturnFromJump) },
                shape = JumpShape,
                color = colors.surfaceVariant,
                border = BorderStroke(OutlineWidth, colors.outline),
                shadowElevation = JumpElevation,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = JumpHeight).padding(start = JumpPaddingStart, end = JumpPaddingEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JumpSpacing),
                ) {
                    Icon(
                        imageVector = if (jump.forward) Icons.AutoMirrored.Filled.Redo else Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(JumpIconSize),
                    )
                    Text(
                        text = stringResource(
                            if (jump.forward) R.string.reader_jump_forward else R.string.reader_jump_back,
                            jump.returnTo,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
