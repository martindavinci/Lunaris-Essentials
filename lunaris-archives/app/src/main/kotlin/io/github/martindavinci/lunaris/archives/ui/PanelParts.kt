package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val HeaderPadding = 24.dp
private val HeaderTop = 4.dp
private val HeaderBottom = 16.dp
private val HeaderGap = 16.dp
private val ActionHeight = 56.dp
private val ActionGap = 20.dp
private val BoxShape = RoundedCornerShape(20.dp)
private val BoxMarginH = 16.dp
private val BoxMarginV = 12.dp
private val BoxPaddingH = 16.dp
private val BoxPaddingV = 8.dp
private val DetailHeight = 36.dp
private val DividerBottom = 8.dp
private const val TITLE_LINES = 2

/** The top of a panel: an optional leading tile, a title and a line under it (frames 05, 16). */
@Composable
internal fun PanelHeader(title: String, subtitle: String?, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = HeaderPadding, end = HeaderPadding, top = HeaderTop, bottom = HeaderBottom),
        horizontalArrangement = Arrangement.spacedBy(HeaderGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(Modifier.padding(bottom = DividerBottom), color = MaterialTheme.colorScheme.surfaceVariant)
}

/** One action of a panel: 56 dp, icon and text. */
@Composable
internal fun PanelActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ActionHeight).clickable(role = Role.Button, onClick = onClick).padding(horizontal = HeaderPadding),
        horizontalArrangement = Arrangement.spacedBy(ActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The rounded box of details under the actions. */
@Composable
internal fun DetailsBox(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = BoxMarginH, vertical = BoxMarginV)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, BoxShape),
    ) {
        Column(Modifier.padding(horizontal = BoxPaddingH, vertical = BoxPaddingV), content = content)
    }
}

/** "Size … 1.2 MB": label on the left, value on the right. */
@Composable
internal fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = DetailHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
internal fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
