package io.github.martindavinci.lunaris.pdf.feature.work

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.ui.theme.currentRow

private val Spacing = 12.dp
private val ButtonHeight = 52.dp
private val ButtonShape = RoundedCornerShape(26.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val SmallIcon = 18.dp
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)
private const val SEPARATOR = " · "

@Composable
internal fun Saved(state: WorkState.Saved, texts: WorkTexts, onDone: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary)
        Text(stringResource(R.string.work_saved), style = MaterialTheme.typography.titleLarge)
    }
    Surface(shape = CardShape, color = colors.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(Spacing)) {
            val many = state.files > 1
            Text(
                text = if (many) pluralStringResource(R.plurals.work_new_files, state.files, state.files) else state.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val size = state.bytes?.takeIf { !many }?.let { Formatter.formatShortFileSize(context, it) }
            val pages = pluralStringResource(R.plurals.pages_split_count, state.pages, state.pages)
            Text(
                text = listOfNotNull(state.folder, pages, size).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
    Text(texts.savedNote, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
        OutlinedButton(onClick = onDone, shape = ButtonShape, modifier = Modifier.weight(1f).heightIn(min = ButtonHeight)) {
            Text(stringResource(R.string.work_done), color = colors.primary)
        }
        if (state.files == 1) {
            MainButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(SmallIcon))
                Text(stringResource(R.string.work_open), modifier = Modifier.padding(start = Spacing / 2))
            }
        }
    }
}

@Composable
internal fun Failed(
    state: WorkState.Failed,
    texts: WorkTexts,
    sourceName: (Int) -> String?,
    onRetry: () -> Unit,
    alternative: String?,
    onAlternative: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing)) {
        Box(Modifier.size(TileSize).clip(TileShape).background(colors.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = colors.onErrorContainer)
        }
        Text(
            stringResource(if (state.kind == JobKind.CREATE) R.string.work_failed_create else R.string.work_failed_save),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    Text(failureText(state, sourceName), style = MaterialTheme.typography.bodyLarge)
    Surface(shape = CardShape, color = colors.currentRow) {
        Row(Modifier.fillMaxWidth().padding(Spacing), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.primary, modifier = Modifier.size(SmallIcon))
            Text(texts.stillHere, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        }
    }
    val needed = state.neededBytes
    val free = state.freeBytes
    if (needed != null && free != null) {
        Text(
            text = stringResource(R.string.work_needed_free, Formatter.formatShortFileSize(context, needed), Formatter.formatShortFileSize(context, free)),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariant,
        )
    }
    MainButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.work_try_again)) }
    if (alternative != null) {
        OutlinedButton(onClick = onAlternative, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
            Text(alternative, color = colors.onSurface)
        }
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.work_close)) }
}

@Composable
private fun failureText(state: WorkState.Failed, sourceName: (Int) -> String?): String {
    val name = state.source?.let(sourceName)
    return when (state.failure) {
        WorkFailure.PROTECTED -> if (name != null) stringResource(R.string.work_why_protected, name) else stringResource(R.string.work_why_protected_this)
        WorkFailure.DAMAGED -> if (name != null) stringResource(R.string.work_why_damaged, name) else stringResource(R.string.work_why_damaged_this)
        WorkFailure.UNREADABLE -> if (name != null) stringResource(R.string.work_why_unreadable, name) else stringResource(R.string.work_why_unreadable_this)
        WorkFailure.MEMORY -> stringResource(R.string.work_why_memory)
        WorkFailure.WRITE -> stringResource(R.string.work_why_write)
        WorkFailure.NO_SPACE -> stringResource(R.string.work_why_space)
    }
}

@Composable
internal fun MainButton(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.heightIn(min = ButtonHeight),
    ) {
        content()
    }
}
