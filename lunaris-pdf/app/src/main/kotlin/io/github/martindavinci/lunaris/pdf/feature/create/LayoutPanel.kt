package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.convert.PageFormat
import io.github.martindavinci.lunaris.pdf.convert.PageMargins
import io.github.martindavinci.lunaris.pdf.convert.TextLayout
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import io.github.martindavinci.lunaris.pdf.ui.theme.currentRow

private val PanelPadding = 24.dp
private val Spacing = 12.dp
private val ChoiceShape = RoundedCornerShape(14.dp)
private val ChoiceHeight = 48.dp
private val ChoicePadding = 8.dp
private val SelectedBorder = 2.dp
private val PlainBorder = 1.dp

/** Page size, its direction and margins, for text, Markdown and HTML. Each is one choice of two or three. */
@Composable
internal fun LayoutPanel(layout: TextLayout, onAction: (CreateAction) -> Unit) {
    val set = { new: TextLayout -> onAction(CreateAction.SetLayout(new)) }
    BottomPanel(onDismiss = { onAction(CreateAction.CloseLayout) }) {
        Column(
            modifier = Modifier.padding(horizontal = PanelPadding).padding(bottom = PanelPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing),
        ) {
            Text(stringResource(R.string.create_layout), style = MaterialTheme.typography.titleLarge)
            Label(R.string.create_layout_size)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                PageFormat.entries.forEach { format ->
                    Choice(formatName(format), layout.format == format, Modifier.weight(1f)) { set(layout.copy(format = format)) }
                }
            }
            Label(R.string.create_layout_direction)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                Choice(stringResource(R.string.create_portrait), !layout.landscape, Modifier.weight(1f)) { set(layout.copy(landscape = false)) }
                Choice(stringResource(R.string.create_landscape), layout.landscape, Modifier.weight(1f)) { set(layout.copy(landscape = true)) }
            }
            Label(R.string.create_layout_margins)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                PageMargins.entries.forEach { margins ->
                    Choice(marginName(margins), layout.margins == margins, Modifier.weight(1f)) { set(layout.copy(margins = margins)) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = ChoiceShape,
        color = if (selected) colors.currentRow else colors.surface,
        border = BorderStroke(if (selected) SelectedBorder else PlainBorder, if (selected) colors.primary else colors.outline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(Modifier.heightIn(min = ChoiceHeight).padding(ChoicePadding), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun marginName(margins: PageMargins): String = when (margins) {
    PageMargins.NARROW -> stringResource(R.string.create_margins_narrow)
    PageMargins.NORMAL -> stringResource(R.string.create_margins_normal)
    PageMargins.WIDE -> stringResource(R.string.create_margins_wide)
}

@Composable
internal fun layoutSummary(layout: TextLayout): String = listOf(
    formatName(layout.format),
    stringResource(if (layout.landscape) R.string.create_landscape else R.string.create_portrait),
    stringResource(
        when (layout.margins) {
            PageMargins.NARROW -> R.string.create_summary_narrow
            PageMargins.NORMAL -> R.string.create_summary_normal
            PageMargins.WIDE -> R.string.create_summary_wide
        },
    ),
).joinToString(" · ")
