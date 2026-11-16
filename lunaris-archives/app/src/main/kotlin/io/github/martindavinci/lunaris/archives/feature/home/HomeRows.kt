package io.github.martindavinci.lunaris.archives.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.ui.FormatTile
import io.github.martindavinci.lunaris.archives.ui.MetaLine
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.sizeText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

private const val DAY_AND_MONTH = "dMMM"
private const val MENU_ROW_ALPHA = 0.06f
private val RowMinHeight = 72.dp
private val RowStart = 16.dp
private val RowEnd = 4.dp
private val RowSpacing = 16.dp
private val MenuShape = RoundedCornerShape(16.dp)
private val MenuWidth = 212.dp
private val MenuItemPadding = PaddingValues(horizontal = 16.dp)

/** One recent archive: format, name, and "12 files · 3.4 MB · 28 Oct" with a lock when it has a password. */
@Composable
internal fun RecentRow(item: RecentArchive, menuOpen: Boolean, onAction: (HomeAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (menuOpen) colors.onSurface.copy(alpha = MENU_ROW_ALPHA) else Color.Transparent)
            .clickable { onAction(HomeAction.OpenRecent(item)) }
            .heightIn(min = RowMinHeight)
            .padding(start = RowStart, end = RowEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        FormatTile(item.formatLabel)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            MetaLine(recentMeta(item), locked = item.locked)
        }
        Box {
            IconButton(onClick = { onAction(HomeAction.ShowMenu(item.uri)) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options), tint = colors.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onAction(HomeAction.HideMenu) },
                modifier = Modifier.width(MenuWidth),
                shape = MenuShape,
                containerColor = colors.surfaceContainer,
            ) {
                MenuItem(R.string.home_menu_remove) { onAction(HomeAction.Remove(item)) }
                MenuItem(R.string.action_share) { onAction(HomeAction.Share(item)) }
                MenuItem(R.string.home_menu_extract_all) { onAction(HomeAction.ExtractAll(item)) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge) }, onClick = onClick, contentPadding = MenuItemPadding)
}

@Composable
private fun recentMeta(item: RecentArchive): String {
    val files = countText(R.plurals.count_files, item.fileCount)
    val size = item.sizeBytes?.let { sizeText(it) }
    return listOfNotNull(files, size, openedText(item.lastOpenedMillis).takeIf { it.isNotEmpty() }).joinToString(SEPARATOR)
}

/** "6:46 PM" for today, "Yesterday", then "2 Nov": in the words of the language in use. Empty when not known. */
@Composable
internal fun openedText(millis: Long): String {
    if (millis <= 0) return ""
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> DateFormat.getTimeFormat(context).format(Date(millis))
        today.minusDays(1) -> stringResource(R.string.recents_yesterday)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_AND_MONTH), locale).format(day)
    }
}
