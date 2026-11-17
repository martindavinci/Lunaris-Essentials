package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R

private val BlockShape = RoundedCornerShape(16.dp)
private val BlockPaddingVertical = 14.dp
private val BlockPaddingHorizontal = 16.dp
private val BlockIconSize = 20.dp
private val BlockSpacing = 12.dp
private val BlockLineSpacing = 6.dp
private val ProblemPadding = 40.dp
private val ProblemSpacing = 16.dp
private val ProblemMaxWidth = 480.dp
private val ProblemTileSize = 72.dp
private val ProblemTileShape = RoundedCornerShape(24.dp)
private val ProblemIconSize = 34.dp
private const val UNSAFE_LINES = 5
/** The lines a caller needs to give: the block shows [UNSAFE_LINES], and one more tells it to offer "Show all". */
const val UNSAFE_PREVIEW_LINES = UNSAFE_LINES + 1
private const val SOFT_LINES = 3

/**
 * The one red block of the app (frame 12): entries that were not written because they could be
 * unsafe. [lines] are ready to show, one entry each, with the reason where it is not "outside".
 */
@Composable
fun UnsafeEntriesBlock(title: String, lines: List<String>, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(shape = BlockShape, color = colors.errorContainer, contentColor = colors.onErrorContainer, modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(horizontal = BlockPaddingHorizontal, vertical = BlockPaddingVertical), horizontalArrangement = Arrangement.spacedBy(BlockSpacing)) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(BlockIconSize))
            Column(verticalArrangement = Arrangement.spacedBy(BlockLineSpacing)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (open) NameList(lines, UNSAFE_LINES, onShowAll)
                if (!open) LinkText(stringResource(R.string.extract_details)) { open = true }
            }
        }
    }
}

/** Entries that were damaged or needed a password that was not given: said plainly, not in red. */
@Composable
fun SoftErrorBlock(title: String, names: List<String>, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = BlockShape, color = colors.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = BlockPaddingHorizontal, vertical = BlockPaddingVertical), horizontalArrangement = Arrangement.spacedBy(BlockSpacing)) {
            Icon(Icons.Rounded.Error, contentDescription = null, tint = colors.error, modifier = Modifier.size(BlockIconSize))
            Column(verticalArrangement = Arrangement.spacedBy(BlockLineSpacing)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                NameList(names, SOFT_LINES, onShowAll)
            }
        }
    }
}

@Composable
private fun NameList(names: List<String>, max: Int, onShowAll: () -> Unit) {
    names.take(max).forEach { name ->
        Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
    if (names.size > max) LinkText(stringResource(R.string.extract_show_all), onShowAll)
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick),
    )
}

/** An archive that cannot be shown: what happened, in calm words, and what to do (frame 09 and the undrawn ones). */
@Composable
fun ProblemState(
    icon: ImageVector,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = ProblemMaxWidth).padding(ProblemPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ProblemSpacing),
        ) {
            Box(Modifier.size(ProblemTileSize).background(colors.surfaceContainerHigh, ProblemTileShape), Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(ProblemIconSize))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            DeepButton(primary.first, primary.second)
            if (secondary != null) AccentTextButton(secondary.first, secondary.second)
        }
    }
}
