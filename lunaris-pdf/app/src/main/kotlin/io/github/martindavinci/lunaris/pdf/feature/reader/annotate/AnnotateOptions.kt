package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationOptions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationPalette
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.feature.reader.AnnotationUiState
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction

private const val THICKNESS_DOT_SCALE = 3
private val RowHeight = 52.dp
private val RowPadding = 12.dp
private val ItemSpacing = 6.dp
private val TouchSize = 44.dp
private val SwatchSize = 28.dp
private val SwatchBorder = 1.dp
private val SelectedBorder = 2.dp
private val GroupGap = 12.dp
private val SegmentShape = RoundedCornerShape(18.dp)
private val SegmentPadding = 14.dp
private val SegmentHeight = 36.dp

internal fun swatchColor(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** The options of the tool in hand, one tap away. */
@Composable
internal fun AnnotateOptions(annotation: AnnotationUiState, onAction: (ReaderAction) -> Unit) {
    val options = annotation.options
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (annotation.tool) {
            AnnotationTool.HIGHLIGHT -> HighlightOptions(options, onAction)
            AnnotationTool.PEN -> PenOptions(options, onAction)
            AnnotationTool.TEXT -> TextOptions(options, onAction)
            AnnotationTool.NOTE -> Hint(R.string.annotate_hint_note)
            AnnotationTool.ERASER -> Hint(R.string.annotate_hint_eraser)
            AnnotationTool.SIGN ->
                if (annotation.placingSignature) {
                    Hint(R.string.annotate_hint_place_signature)
                } else {
                    OutlinedButton(onClick = { onAction(EditAction.ChooseTool(AnnotationTool.SIGN)) }) {
                        Text(stringResource(R.string.annotate_signatures))
                    }
                }
        }
    }
}

@Composable
private fun Hint(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RowScope.HighlightOptions(options: AnnotationOptions, onAction: (ReaderAction) -> Unit) {
    Segment(R.string.annotate_highlight_text, !options.highlightFree) { onAction(EditAction.SetHighlightFree(false)) }
    Segment(R.string.annotate_highlight_free, options.highlightFree) { onAction(EditAction.SetHighlightFree(true)) }
    Spacer(Modifier.weight(1f))
    Swatches(AnnotationPalette.HIGHLIGHT_COLORS, options.highlightColor, onAction)
}

@Composable
private fun Segment(label: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = SegmentShape,
        color = if (selected) colors.primaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
        Box(Modifier.heightIn(min = SegmentHeight).padding(horizontal = SegmentPadding), contentAlignment = Alignment.Center) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RowScope.PenOptions(options: AnnotationOptions, onAction: (ReaderAction) -> Unit) {
    Swatches(AnnotationPalette.PEN_COLORS, options.penColor, onAction)
    Spacer(Modifier.weight(1f))
    AnnotationPalette.PEN_THICKNESSES.forEachIndexed { index, thickness ->
        ThicknessDot(index, thickness, thickness == options.penThickness, onAction)
    }
}

@Composable
private fun ThicknessDot(index: Int, thickness: Int, selected: Boolean, onAction: (ReaderAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(THICKNESS_NAMES[index])
    Box(
        modifier = Modifier
            .size(TouchSize)
            .then(if (selected) Modifier.background(colors.primaryContainer, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton) { onAction(EditAction.ChoosePenThickness(thickness)) }
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((thickness * THICKNESS_DOT_SCALE).dp)
                .background(if (selected) colors.onPrimaryContainer else colors.onSurface, CircleShape),
        )
    }
}

private val THICKNESS_NAMES = listOf(R.string.annotate_thin, R.string.annotate_medium, R.string.annotate_thick)

@Composable
private fun RowScope.TextOptions(options: AnnotationOptions, onAction: (ReaderAction) -> Unit) {
    Swatches(AnnotationPalette.TEXT_COLORS, options.textColor, onAction)
    Spacer(Modifier.weight(1f))
    IconButton(onClick = { onAction(EditAction.ChangeTextSize(-1)) }) {
        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.annotate_text_smaller))
    }
    Text(options.textSize.toString(), style = MaterialTheme.typography.labelLarge)
    IconButton(onClick = { onAction(EditAction.ChangeTextSize(1)) }) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.annotate_text_larger))
    }
    Spacer(Modifier.width(GroupGap))
    IconButton(onClick = { onAction(EditAction.DeleteSelected) }) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.annotate_delete_selected))
    }
}

@Composable
private fun Swatches(palette: List<String>, chosen: String, onAction: (ReaderAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        palette.forEach { hex -> Swatch(hex, hex == chosen, onClick = { onAction(EditAction.ChooseColor(hex)) }) }
    }
}

@Composable
private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(colorName(hex))
    val edge = if (selected) BorderStroke(SelectedBorder, colors.primary) else BorderStroke(SwatchBorder, colors.outline)
    Box(
        modifier = Modifier
            .size(TouchSize)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SwatchSize)
                .background(swatchColor(hex), CircleShape)
                .border(edge, CircleShape),
        )
    }
}

private fun colorName(hex: String): Int = when (hex) {
    AnnotationPalette.HIGHLIGHT_COLORS[0] -> R.string.annotate_color_yellow
    AnnotationPalette.HIGHLIGHT_COLORS[1], AnnotationPalette.PEN_COLORS[3] -> R.string.annotate_color_green
    AnnotationPalette.HIGHLIGHT_COLORS[3] -> R.string.annotate_color_pink
    AnnotationPalette.PEN_COLORS[0] -> R.string.annotate_color_black
    AnnotationPalette.PEN_COLORS[1] -> R.string.annotate_color_red
    else -> R.string.annotate_color_blue
}
