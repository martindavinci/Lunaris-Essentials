package io.github.martindavinci.lunaris.scanner.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val ScreenPadding = 16.dp
private val SectionSpacing = 12.dp
private val ActionSpacing = 8.dp

@Composable
internal fun CreateScreen(
    state: CreateUiState,
    snackbar: SnackbarHostState,
    onAction: (CreateAction) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            ChoiceChips(
                options = CreateType.entries,
                selected = state.form.type,
                label = { labelOf(it) },
                onSelect = { onAction(CreateAction.SelectType(it)) },
            )
            CreateFields(state.form) { onAction(CreateAction.EditForm(it)) }
            ErrorLine(state)
            FormatPicker(state.formats, state.form.format) { onAction(CreateAction.SelectFormat(it)) }
            CodePreview(
                image = state.preview,
                sizePx = state.previewSizePx,
                onSized = { onAction(CreateAction.PreviewSized(it)) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Actions(
                enabled = state.result is PayloadResult.Ready,
                onSave = onSave,
                onShare = { onAction(CreateAction.Share) },
                onCopy = onCopy,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ErrorLine(state: CreateUiState) {
    val message = when {
        state.result is PayloadResult.Invalid -> messageOf(state.result.reason)
        state.encodeFailed -> R.string.create_error_encode
        else -> return
    }
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun FormatPicker(formats: List<CodeFormat>, selected: CodeFormat, onSelect: (CodeFormat) -> Unit) {
    val locked = formats.size == 1
    Column(verticalArrangement = Arrangement.spacedBy(ActionSpacing)) {
        Text(
            stringResource(if (locked) R.string.create_format_locked else R.string.create_format_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceChips(
            options = formats,
            selected = selected,
            label = { labelOf(it) },
            onSelect = onSelect,
            enabled = !locked,
        )
    }
}

@Composable
private fun Actions(enabled: Boolean, onSave: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit) {
    val padding = ButtonDefaults.TextButtonContentPadding
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ActionSpacing)) {
        Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = enabled, contentPadding = padding) {
            Text(stringResource(R.string.create_save))
        }
        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), enabled = enabled, contentPadding = padding) {
            Text(stringResource(R.string.share))
        }
        OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f), enabled = enabled, contentPadding = padding) {
            Text(stringResource(R.string.copy))
        }
    }
}

private const val PREVIEW_MODULES = 21
private const val PREVIEW_FINDER_OFFSET = 1
private const val PREVIEW_FINDER_SIZE = 7
private const val PREVIEW_IMAGE_PX = 420

@Preview(showBackground = true)
@Composable
private fun CreateScreenPreview() {
    val matrix = BitMatrix(PREVIEW_MODULES).apply {
        setRegion(PREVIEW_FINDER_OFFSET, PREVIEW_FINDER_OFFSET, PREVIEW_FINDER_SIZE, PREVIEW_FINDER_SIZE)
    }
    LunarisScannerTheme {
        CreateScreen(
            state = CreateUiState(
                form = CreateForm(text = "Lunaris"),
                formats = listOf(CodeFormat.QR_CODE, CodeFormat.AZTEC, CodeFormat.DATA_MATRIX),
                result = PayloadResult.Ready("Lunaris", CodeFormat.QR_CODE),
                preview = CodeImage.of(matrix, PREVIEW_IMAGE_PX),
                previewSizePx = PREVIEW_IMAGE_PX,
            ),
            snackbar = remember { SnackbarHostState() },
            onAction = {},
            onSave = {},
            onCopy = {},
        )
    }
}
