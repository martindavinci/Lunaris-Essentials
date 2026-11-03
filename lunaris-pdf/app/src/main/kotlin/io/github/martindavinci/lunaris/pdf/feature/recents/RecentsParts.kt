package io.github.martindavinci.lunaris.pdf.feature.recents

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.recents.RecentsHome
import io.github.martindavinci.lunaris.pdf.recents.WhenLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date

private const val DAY_AND_MONTH = "dMMM"
private const val SEPARATOR = " · "

private val EdgePadding = 16.dp
private val CardMargin = 16.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardPadding = 16.dp
private val CardSpacing = 16.dp
private val CardThumbWidth = 72.dp
private val CardThumbHeight = 100.dp
private val RowThumbWidth = 36.dp
private val RowThumbHeight = 48.dp
private val RowSpacing = 16.dp
private val RowPaddingVertical = 8.dp
private val SectionTopPadding = 16.dp
private val SectionBottomPadding = 4.dp
private val ProgressHeight = 4.dp
private val ProgressShape = RoundedCornerShape(2.dp)
private val ProgressTopPadding = 12.dp
private val ProgressSpacing = 12.dp
private val LockIconSize = 14.dp
private val LockSpacing = 4.dp

@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = EdgePadding, end = EdgePadding, top = SectionTopPadding, bottom = SectionBottomPadding),
    )
}

/** The document being read: one tap to go on. */
@Composable
internal fun ResumeCard(entry: RecentEntry, thumbnail: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardMargin).padding(top = SectionBottomPadding),
    ) {
        Row(Modifier.padding(CardPadding), horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
            DocumentThumb(entry, thumbnail, CardThumbWidth, CardThumbHeight)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recents_continue), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = entry.chapter.ifEmpty { openedLabel(entry) }
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(ProgressTopPadding))
                ReadingProgress(entry)
            }
        }
    }
}

@Composable
private fun ReadingProgress(entry: RecentEntry) {
    val colors = MaterialTheme.colorScheme
    if (entry.pages <= 0) {
        Text(stringResource(R.string.recents_page, entry.page), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ProgressSpacing)) {
        val fraction = (entry.page.toFloat() / entry.pages).coerceIn(0f, 1f)
        Box(Modifier.weight(1f).height(ProgressHeight).clip(ProgressShape).background(colors.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction).height(ProgressHeight).background(colors.primary))
        }
        Text(
            text = stringResource(R.string.reader_page_short, entry.page, entry.pages),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RecentRow(entry: RecentEntry, thumbnail: suspend (String) -> ImageBitmap?, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = EdgePadding, top = RowPaddingVertical, bottom = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        DocumentThumb(entry, thumbnail, RowThumbWidth, RowThumbHeight)
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RecentMeta(entry)
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RecentMeta(entry: RecentEntry) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LockSpacing)) {
        if (entry.locked) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.recents_locked), tint = color, modifier = Modifier.size(LockIconSize))
        }
        val page = if (entry.pages > 0) {
            stringResource(R.string.reader_page_of, entry.page, entry.pages)
        } else {
            stringResource(R.string.recents_page, entry.page)
        }
        val opened = openedLabel(entry)
        Text(
            text = if (opened.isEmpty()) page else page + SEPARATOR + opened,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** When it was opened, in the words of the language in use. Empty if that is not known. */
@Composable
private fun openedLabel(entry: RecentEntry): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    return when (val label = RecentsHome.whenLabel(entry.lastOpenedMillis, System.currentTimeMillis(), zone)) {
        null -> ""
        is WhenLabel.At -> DateFormat.getTimeFormat(context).format(Date(label.millis))
        WhenLabel.Yesterday -> stringResource(R.string.recents_yesterday)
        is WhenLabel.Weekday -> label.day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        is WhenLabel.Day -> DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_AND_MONTH), locale)
            .format(label.date)
    }
}
