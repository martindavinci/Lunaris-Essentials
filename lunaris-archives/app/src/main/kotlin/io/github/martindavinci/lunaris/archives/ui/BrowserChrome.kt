package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.ui.theme.TabularNumbers

private val FieldMinHeight = 48.dp
private val FieldShape = RoundedCornerShape(24.dp)
private val FieldStart = 18.dp
private val FieldEnd = 4.dp
private val FabMinHeight = 48.dp
private val FabShape = RoundedCornerShape(16.dp)
private val FabStart = 16.dp
private val FabEnd = 20.dp
private val FabIconSize = 22.dp
private val FabSpacing = 10.dp
private val FloatingElevation = 6.dp
private val OpeningShape = RoundedCornerShape(16.dp)
private val OpeningRowMinHeight = 52.dp
private val OpeningStart = 16.dp
private val OpeningEnd = 6.dp
private val OpeningSpacing = 12.dp
private val OpeningLine = 3.dp

/** The search field of frames 07 and of Home: takes the focus when it appears. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(shape = FieldShape, color = colors.surfaceContainerHigh, modifier = modifier) {
        Row(Modifier.heightIn(min = FieldMinHeight).padding(start = FieldStart, end = FieldEnd), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Extract all": an extended button 48 dp high, which Material's own cannot be. The text hides while the list scrolls down. */
@Composable
fun CompactExtendedFab(icon: ImageVector, text: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = FabShape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shadowElevation = FloatingElevation,
        modifier = modifier.semantics { contentDescription = text },   // also when the text is hidden
    ) {
        Row(
            Modifier.heightIn(min = FabMinHeight).padding(start = FabStart, end = if (expanded) FabEnd else FabStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FabSpacing),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(FabIconSize))
            AnimatedVisibility(visible = expanded) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        }
    }
}

/** "Opening plan.pdf… 64%" with Cancel (frame 04). [progress] below zero: how much is not known. */
@Composable
fun OpeningBar(name: String, progress: Float, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val percent = if (progress >= 0f) percentText(progress) else ""
    val opening = stringResource(R.string.browser_opening, name)
    Surface(shape = OpeningShape, color = colors.surfaceVariant, shadowElevation = FloatingElevation, modifier = modifier) {
        Column {
            Row(
                Modifier.heightIn(min = OpeningRowMinHeight).padding(start = OpeningStart, end = OpeningEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OpeningSpacing),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(opening)
                        if (percent.isNotEmpty()) withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(" $percent") }
                    },
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AccentTextButton(stringResource(R.string.cancel), onCancel)
            }
            Box(Modifier.fillMaxWidth().height(OpeningLine).background(colors.outline)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(OpeningLine).background(colors.primary))
            }
        }
    }
}
