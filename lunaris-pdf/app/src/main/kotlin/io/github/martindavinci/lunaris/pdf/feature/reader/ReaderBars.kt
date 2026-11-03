package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

private const val LONGEST_PAGE_LABEL = 16
private val TopBarHeight = 64.dp
private val BottomBarHeight = 72.dp
private val BarEdgePadding = 12.dp
private val PillHeight = 44.dp
private val PillShape = RoundedCornerShape(22.dp)
private val PillPadding = 16.dp
private val PillSpacing = 4.dp
private val PillIconSize = 18.dp
private val ActionSize = 48.dp

@Composable
internal fun ReaderTopBar(
    name: String,
    chapter: String?,
    night: Boolean,
    onBack: () -> Unit,
    canPrint: Boolean,
    onAction: (ReaderAction) -> Unit,
    onGoToPage: () -> Unit,
    onPrint: () -> Unit,
    onSaveCopy: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuItem: @Composable (Int, () -> Unit) -> Unit = { label, action ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                menuOpen = false
                action()
            },
        )
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chapter != null) {
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { onAction(ReaderAction.OpenSearch) }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more))
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menuItem(R.string.reader_go_to_page, onGoToPage)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_night_pages)) },
                        trailingIcon = { Checkbox(checked = night, onCheckedChange = null) },
                        onClick = {
                            menuOpen = false
                            onAction(ReaderAction.ToggleNight)
                        },
                    )
                    if (canPrint) menuItem(R.string.action_print, onPrint)
                    menuItem(R.string.action_share) { onAction(ReaderAction.Share) }
                    menuItem(R.string.action_save_copy, onSaveCopy)
                }
            }
        }
    }
}

/** The page goes to a page; the three actions of reading stay under the thumb. */
@Composable
internal fun ReaderBottomBar(
    page: Int,
    pages: Int,
    night: Boolean,
    onAction: (ReaderAction) -> Unit,
    onGoToPage: () -> Unit,
    onOutline: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = BottomBarHeight)
                .padding(horizontal = BarEdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagePill(page, pages, onGoToPage)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onAction(ReaderAction.StartReadAloud) }) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = stringResource(R.string.read_aloud))
            }
            NightButton(night, onClick = { onAction(ReaderAction.ToggleNight) })
            IconButton(onClick = onOutline) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_outline))
            }
        }
    }
}

@Composable
private fun PagePill(page: Int, pages: Int, onClick: () -> Unit) {
    val long = stringResource(R.string.reader_page_of, page, pages)
    val label = if (long.length > LONGEST_PAGE_LABEL) stringResource(R.string.reader_page_short, page, pages) else long
    Surface(onClick = onClick, shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.heightIn(min = PillHeight).padding(horizontal = PillPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillSpacing),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Filled.UnfoldMore, contentDescription = null, modifier = Modifier.size(PillIconSize))
        }
    }
}

@Composable
private fun NightButton(on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(ActionSize)
            .then(if (on) Modifier.background(colors.primaryContainer, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DarkMode,
            contentDescription = stringResource(R.string.reader_night_pages),
            tint = if (on) colors.onPrimaryContainer else colors.onSurface,
        )
    }
}
