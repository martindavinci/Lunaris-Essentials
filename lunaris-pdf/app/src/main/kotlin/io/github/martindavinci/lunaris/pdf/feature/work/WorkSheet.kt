package io.github.martindavinci.lunaris.pdf.feature.work

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.ui.theme.currentRow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val SCRIM_ALPHA = 0.62f
private const val PERCENT = 100
private const val SHOW_AFTER_MILLIS = 1_000L
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SheetPadding = 20.dp
private val Spacing = 12.dp
private val ButtonHeight = 52.dp
private val ButtonShape = RoundedCornerShape(26.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val SmallIcon = 18.dp
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)

/** What a screen tells the sheet. The rest comes from the state of the job. */
data class WorkTexts(val noteWhileRunning: String, val cancelNote: String, val stillHere: String, val savedNote: String)

/** Over the screen until the job is over. A tap outside does nothing; back is the same as Cancel, or Close. */
@Composable
internal fun WorkSheet(
    state: WorkState,
    texts: WorkTexts,
    sourceName: (Int) -> String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    alternative: String?,
    onAlternative: () -> Unit,
    onCloseFailure: () -> Unit,
    onDone: () -> Unit,
    onOpen: () -> Unit,
) {
    BackHandler(onBack = if (state is WorkState.Running) onCancel else if (state is WorkState.Failed) onCloseFailure else onDone)
    // A job of less than a second goes straight to the result, with nothing flashing in between.
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MILLIS)
        showProgress = true
    }
    val visible = state !is WorkState.Running || showProgress
    Box(
        Modifier
            .fillMaxSize()
            .background(if (visible) Color.Black.copy(alpha = SCRIM_ALPHA) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        if (!visible) return@Box
        Surface(shape = SheetShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(SheetPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing),
            ) {
                when (state) {
                    is WorkState.Running -> Running(state, texts, sourceName, onCancel)
                    is WorkState.Saved -> Saved(state, texts, onDone, onOpen)
                    is WorkState.Failed -> Failed(state, texts, sourceName, onRetry, alternative, onAlternative, onCloseFailure)
                }
            }
        }
    }
}

@Composable
private fun Running(state: WorkState.Running, texts: WorkTexts, sourceName: (Int) -> String?, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(
            when (state.kind) {
                JobKind.SAVE -> R.string.work_saving
                JobKind.EXTRACT -> R.string.work_extracting
                JobKind.SPLIT -> R.string.work_splitting
                JobKind.CREATE -> R.string.work_creating
            },
        ),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = listOfNotNull(state.name, state.folder).joinToString(SEPARATOR),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(runningDetail(state, sourceName), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("${(state.fraction * PERCENT).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
    if (texts.noteWhileRunning.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(SmallIcon))
            Text(texts.noteWhileRunning, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    OutlinedButton(onClick = onCancel, shape = ButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight)) {
        Text(stringResource(R.string.cancel), color = colors.primary)
    }
    Text(
        text = texts.cancelNote,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun runningDetail(state: WorkState.Running, sourceName: (Int) -> String?): String {
    val preparing = state.preparing
    return when {
        preparing != null -> stringResource(R.string.work_adding, sourceName(preparing).orEmpty(), preparing + 1, state.preparingOf)
        state.pages == 0 -> stringResource(R.string.work_preparing)
        state.outputs > 1 -> stringResource(R.string.work_file_page_of, state.output + 1, state.outputs, state.page, state.pages)
        else -> stringResource(R.string.work_page_of, state.page, state.pages)
    }
}

@Composable
private fun Saved(state: WorkState.Saved, texts: WorkTexts, onDone: () -> Unit, onOpen: () -> Unit) {
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
private fun Failed(
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
private fun MainButton(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
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

private const val SEPARATOR = " · "
