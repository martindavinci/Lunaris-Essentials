package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.history.icon
import io.github.martindavinci.lunaris.scanner.feature.history.kind
import io.github.martindavinci.lunaris.scanner.feature.history.relativeDate
import io.github.martindavinci.lunaris.scanner.feature.result.ResultAction
import io.github.martindavinci.lunaris.scanner.feature.result.kindRes
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SheetPadding = 16.dp
private val ButtonIconGap = 8.dp
private const val SHEET_HEIGHT = 0.92f
private const val SCRIM_ALPHA = 0.5f

/** Over the camera, which waits meanwhile. Only this round's codes: the rest is in the History tab. */
@Composable
internal fun SessionList(
    session: List<SessionCode>,
    onClose: () -> Unit,
    onDetails: (ScannedCode) -> Unit,
    onRun: (ResultAction) -> Unit,
) {
    val text = remember(session) { sessionText(session) }
    val sensitive = remember(session) { sessionIsSensitive(session) }
    val now = remember(session) { System.currentTimeMillis() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Surface(
            shape = SheetShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(SHEET_HEIGHT),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SheetPadding, vertical = ButtonIconGap),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pluralStringResource(R.plurals.scan_session_count, session.size, session.size), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onRun(ResultAction.Copy(text, sensitive)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(ButtonIconGap))
                        Text(stringResource(R.string.scan_copy_all))
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(session, key = { it.code.format.name + it.code.text }) { entry -> SessionRow(entry, now, onDetails) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SheetPadding),
                    horizontalArrangement = Arrangement.spacedBy(ButtonIconGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(ButtonIconGap))
                        Text(stringResource(R.string.scan_keep_scanning))
                    }
                    IconButton(onClick = { onRun(ResultAction.Share(text)) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(entry: SessionCode, now: Long, onDetails: (ScannedCode) -> Unit) {
    val card = remember(entry.code) { codeCard(entry.code) }
    ListItem(
        headlineContent = {
            Text(card.summary.ifEmpty { stringResource(card.content.kindRes) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(listOfNotNull(stringResource(card.content.kindRes), relativeDate(entry.readAtMillis, now)).joinToString(" · "))
        },
        leadingContent = { Icon(card.content.kind().icon(), contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onDetails(entry.code) },
    )
    HorizontalDivider()
}
