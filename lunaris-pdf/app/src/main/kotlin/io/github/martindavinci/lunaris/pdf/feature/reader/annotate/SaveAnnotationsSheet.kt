package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction
import io.github.martindavinci.lunaris.pdf.feature.reader.SaveSheet
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel

private const val BLOCKED_ALPHA = 0.5f
private val SheetPadding = 20.dp
private val SheetSpacing = 12.dp
private val CardShape = RoundedCornerShape(16.dp)
private val CardPadding = 16.dp
private val LineSpacing = 2.dp

/** What happens to the changes. The new file comes first: it is the choice that risks nothing. */
@Composable
internal fun SaveAnnotationsSheet(sheet: SaveSheet, canWrite: Boolean, onAction: (ReaderAction) -> Unit, onSaveCopy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BottomPanel(onDismiss = { onAction(EditAction.KeepEditing) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = SheetPadding, end = SheetPadding, bottom = SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SheetSpacing),
        ) {
            Text(stringResource(R.string.annotate_save_title), style = MaterialTheme.typography.titleLarge)
            Text(summary(sheet), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (sheet.signed) {
                Surface(shape = CardShape, color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                    Column(Modifier.fillMaxWidth().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
                        Text(stringResource(R.string.annotate_signed_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.annotate_signed_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Choice(
                title = stringResource(R.string.action_save_copy),
                detail = stringResource(R.string.annotate_save_copy_detail),
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                onClick = onSaveCopy,
            )
            Choice(
                title = stringResource(R.string.annotate_save_over),
                detail = stringResource(if (canWrite) R.string.annotate_save_over_detail else R.string.annotate_save_over_blocked),
                container = colors.surfaceVariant,
                content = if (canWrite) colors.onSurface else colors.onSurface.copy(alpha = BLOCKED_ALPHA),
                enabled = canWrite,
                onClick = { onAction(EditAction.SaveOver) },
            )
            TextButton(onClick = { onAction(EditAction.Discard) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.annotate_discard), color = colors.error)
            }
            TextButton(onClick = { onAction(EditAction.KeepEditing) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.annotate_keep_editing))
            }
        }
    }
}

@Composable
private fun summary(sheet: SaveSheet): String {
    val changes = pluralStringResource(R.plurals.annotate_changes, sheet.changes, sheet.changes)
    // A form says nothing of its page: then only the changes are counted.
    return if (sheet.pages > 0) {
        stringResource(R.string.annotate_summary, pluralStringResource(R.plurals.annotate_pages, sheet.pages, sheet.pages), changes)
    } else {
        changes
    }
}

@Composable
private fun Choice(title: String, detail: String, container: Color, content: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CardShape, color = container, contentColor = content) {
        Column(Modifier.fillMaxWidth().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
