package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import io.github.martindavinci.lunaris.scanner.document.PageSize
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val TopBarMinHeight = 56.dp
private val PreviewPadding = 12.dp
private val ControlsSpacing = 8.dp
private val ControlsPadding = 12.dp

/** The bar of the full-screen steps. Insets were consumed by the app scaffold around the routes. */
@Composable
internal fun DocumentTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TopBarMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** On a wide screen the page and its controls stand side by side, since they would not fit one over the other. */
@Composable
internal fun ReviewScreen(
    state: DocumentUiState,
    onAction: (DocumentAction) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onSavePdf: () -> Unit,
    onSavePng: () -> Unit,
    onSaveJpeg: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DocumentTopBar(
            title = stringResource(R.string.document_page_of, state.current + 1, state.pages.size),
            onBack = { onAction(DocumentAction.BackPressed) },
            actions = { MoreMenu(onSavePng, onSaveJpeg) },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val controls = @Composable { modifier: Modifier ->
                ReviewControls(state, onAction, onPickImage, onTakePhoto, onSavePdf, modifier.padding(ControlsPadding))
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    PagePreview(state, Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ControlsSpacing)) {
                    PagePreview(state, Modifier.weight(1f).fillMaxWidth())
                    controls(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Until the page is ready its thumbnail stands in, so that nothing flashes when moving between pages. */
@Composable
private fun PagePreview(state: DocumentUiState, modifier: Modifier) {
    Box(modifier.padding(PreviewPadding), contentAlignment = Alignment.Center) {
        val shown = state.result ?: state.currentPage?.thumbnail
        if (shown != null) {
            Image(
                bitmap = rememberPageImage(shown),
                contentDescription = stringResource(R.string.document_page_of, state.current + 1, state.pages.size),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.rendering || shown == null) CircularProgressIndicator()
    }
}

@Composable
private fun MoreMenu(onSavePng: () -> Unit, onSaveJpeg: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.document_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_save_png)) },
                onClick = {
                    open = false
                    onSavePng()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_save_jpeg)) },
                onClick = {
                    open = false
                    onSaveJpeg()
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ReviewScreenPreview() {
    val page = PagePixels(IntArray(12) { -1 }, 3, 4)
    LunarisScannerTheme {
        ReviewScreen(
            state = DocumentUiState(
                step = DocumentStep.REVIEW,
                pages = List(2) { DocumentPage(it.toLong(), "", PageSize(3, 4), PageSettings(), page) },
                result = page,
            ),
            onAction = {},
            onPickImage = {},
            onTakePhoto = {},
            onSavePdf = {},
            onSavePng = {},
            onSaveJpeg = {},
        )
    }
}
