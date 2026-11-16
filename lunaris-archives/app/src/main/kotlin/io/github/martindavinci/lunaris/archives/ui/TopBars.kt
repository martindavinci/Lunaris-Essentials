package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.ui.theme.BarTitleStyle
import io.github.martindavinci.lunaris.archives.ui.theme.accentText

private val BarMinHeight = 64.dp
private val BarPadding = 4.dp
private val TitleStart = 4.dp
private val TitleSpacing = 8.dp
private val CrumbRowMinHeight = 44.dp
private val CrumbRowPadding = 8.dp
private val CrumbMinHeight = 36.dp
private val CrumbPadding = 10.dp
private val CrumbShape = RoundedCornerShape(18.dp)
private val CrumbIconSize = 18.dp
private val CrumbSpacing = 6.dp

/** Back, the archive's name with its format, then the actions of the screen. System bar insets are the caller's. */
@Composable
fun ArchiveTopBar(name: String, format: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(horizontal = BarPadding), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Row(
            modifier = Modifier.weight(1f).padding(start = TitleStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TitleSpacing),
        ) {
            Text(name, style = BarTitleStyle, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
            if (format.isNotEmpty()) FormatBadge(format)
        }
        actions()
    }
}

/** A bar with one button and a title: New archive, Settings. */
@Composable
fun SimpleTopBar(
    title: String,
    navIcon: ImageVector,
    navLabel: String,
    onNav: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    Row(
        modifier.fillMaxWidth().background(background).heightIn(min = BarMinHeight).padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNav) { Icon(navIcon, contentDescription = navLabel) }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = TitleStart))
    }
}

data class CrumbSegment(val label: String, val isArchive: Boolean)

/** Where the user is inside the archive (and inside archives within it). The last segment is the current folder. */
@Composable
fun Breadcrumb(segments: List<CrumbSegment>, onSegment: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val state = rememberLazyListState()
    // The current folder is always in view; earlier ones scroll off to the start.
    LaunchedEffect(segments.size) { if (segments.isNotEmpty()) state.animateScrollToItem(segments.lastIndex) }
    Column(modifier) {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth().heightIn(min = CrumbRowMinHeight),
            contentPadding = PaddingValues(horizontal = CrumbRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(segments) { index, segment ->
                val current = index == segments.lastIndex
                val tint = if (current) colors.onSurface else colors.accentText
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(CrumbIconSize))
                    }
                    Row(
                        modifier = Modifier
                            .clip(CrumbShape)
                            .clickable(role = Role.Button) { onSegment(index) }
                            .heightIn(min = CrumbMinHeight)
                            .padding(horizontal = CrumbPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CrumbSpacing),
                    ) {
                        if (segment.isArchive) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = tint, modifier = Modifier.size(CrumbIconSize))
                        }
                        Text(segment.label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
                    }
                }
            }
        }
        HorizontalDivider(color = colors.surfaceVariant)
    }
}
