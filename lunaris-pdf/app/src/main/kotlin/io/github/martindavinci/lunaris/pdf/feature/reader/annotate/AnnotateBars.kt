package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationOptions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.feature.reader.AnnotationUiState
import io.github.martindavinci.lunaris.pdf.feature.reader.DocumentEdits
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction

private const val STATUS_ALPHA = 0.75f
private const val DISABLED_ALPHA = 0.38f
private val BarHeight = 64.dp
private val BarEdge = 12.dp
private val DoneHeight = 40.dp
private val DoneShape = RoundedCornerShape(20.dp)
private val DonePadding = 14.dp
private val DoneSpacing = 6.dp
private val DoneIcon = 18.dp
private val TitlePadding = 12.dp
private val ToolPaddingVertical = 8.dp
private val ToolIconBox = 32.dp
private val ToolShape = RoundedCornerShape(16.dp)
private val ToolIconPaddingHorizontal = 12.dp
private val ToolLabelTop = 2.dp
private val DotSize = 8.dp
private val DotOffset = 2.dp
private val DotBorder = 1.dp

/** Deep blue says the mode at once. Done is the way out, and where the saving is decided. */
@Composable
internal fun AnnotateTopBar(edits: DocumentEdits, onAction: (ReaderAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = BarHeight).padding(start = BarEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { onAction(EditAction.Finish) },
                shape = DoneShape,
                color = colors.onPrimaryContainer,
                contentColor = colors.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = DoneHeight).padding(horizontal = DonePadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DoneSpacing),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(DoneIcon))
                    Text(stringResource(R.string.annotate_done), style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = TitlePadding)) {
                Text(
                    stringResource(R.string.annotate_title),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (edits.changes > 0) {
                        pluralStringResource(R.plurals.annotate_changes_unsaved, edits.changes, edits.changes)
                    } else {
                        stringResource(R.string.annotate_no_changes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = STATUS_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onAction(EditAction.Undo) }, enabled = edits.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.annotate_undo), tint = enabledTint(edits.canUndo))
            }
            IconButton(onClick = { onAction(EditAction.Redo) }, enabled = edits.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.annotate_redo), tint = enabledTint(edits.canRedo))
            }
        }
    }
}

@Composable
private fun enabledTint(enabled: Boolean) =
    if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = DISABLED_ALPHA)

/** Where the bar of the reader was. It rides above the keyboard, and the options of the tool ride above it. */
@Composable
internal fun AnnotateToolBar(annotation: AnnotationUiState, onAction: (ReaderAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            AnnotateOptions(annotation, onAction)
            Row(Modifier.fillMaxWidth().padding(vertical = ToolPaddingVertical)) {
                AnnotationTool.entries.forEach { tool ->
                    ToolButton(
                        tool = tool,
                        selected = tool == annotation.tool,
                        dot = dotColor(tool, annotation.options),
                        onClick = { onAction(EditAction.ChooseTool(tool)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun dotColor(tool: AnnotationTool, options: AnnotationOptions): String? = when (tool) {
    AnnotationTool.HIGHLIGHT -> options.highlightColor
    AnnotationTool.PEN -> options.penColor
    AnnotationTool.TEXT -> options.textColor
    else -> null
}

@Composable
private fun ToolButton(tool: AnnotationTool, selected: Boolean, dot: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = colors.surface, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .heightIn(min = ToolIconBox)
                    .then(if (selected) Modifier.background(colors.primaryContainer, ToolShape) else Modifier)
                    .padding(horizontal = ToolIconPaddingHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = tool.icon(),
                    contentDescription = null,
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
                if (dot != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = DotOffset, y = -DotOffset)
                            .size(DotSize)
                            .background(swatchColor(dot), CircleShape)
                            .border(DotBorder, colors.outline, CircleShape),
                    )
                }
            }
            Text(
                text = stringResource(tool.label),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = ToolLabelTop),
            )
        }
    }
}

private val AnnotationTool.label: Int
    get() = when (this) {
        AnnotationTool.HIGHLIGHT -> R.string.annotate_tool_highlight
        AnnotationTool.PEN -> R.string.annotate_tool_pen
        AnnotationTool.TEXT -> R.string.annotate_tool_text
        AnnotationTool.NOTE -> R.string.annotate_tool_note
        AnnotationTool.SIGN -> R.string.annotate_tool_sign
        AnnotationTool.ERASER -> R.string.annotate_tool_eraser
    }

@Composable
private fun AnnotationTool.icon(): Painter = when (this) {
    AnnotationTool.HIGHLIGHT -> rememberVectorPainter(Icons.Filled.BorderColor)
    AnnotationTool.PEN -> rememberVectorPainter(Icons.Filled.Edit)
    AnnotationTool.TEXT -> rememberVectorPainter(Icons.Filled.TextFields)
    AnnotationTool.NOTE -> rememberVectorPainter(Icons.Filled.StickyNote2)
    AnnotationTool.SIGN -> rememberVectorPainter(Icons.Filled.Draw)
    AnnotationTool.ERASER -> painterResource(R.drawable.ic_eraser)
}
