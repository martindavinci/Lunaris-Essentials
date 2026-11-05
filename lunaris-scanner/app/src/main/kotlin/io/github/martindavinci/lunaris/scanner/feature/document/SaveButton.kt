package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val TitlePadding = 24.dp
private val CancelPadding = 12.dp

@Composable
internal fun SaveButton(
    pageCount: Int,
    onSavePdf: () -> Unit,
    onSavePicture: (PictureFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }, modifier = modifier) {
        Text(stringResource(R.string.document_save))
    }
    if (open) {
        SaveDialog(
            pageCount = pageCount,
            onSavePdf = {
                open = false
                onSavePdf()
            },
            onSavePicture = { format ->
                open = false
                onSavePicture(format)
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * Each format with what it will give: a PDF holds every page in one file, while a picture holds one, so
 * several pages make several files. Whole rows are the touch targets, so it is a plain dialog on a surface.
 */
@Composable
private fun SaveDialog(
    pageCount: Int,
    onSavePdf: () -> Unit,
    onSavePicture: (PictureFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val several = pageCount > 1
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.document_save_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(TitlePadding),
                )
                FormatRow(
                    icon = Icons.Filled.PictureAsPdf,
                    name = R.string.document_format_pdf,
                    detail = if (several) R.string.document_format_pdf_pages else R.string.document_format_pdf_one,
                    onClick = onSavePdf,
                )
                FormatRow(
                    icon = Icons.Filled.Image,
                    name = R.string.document_format_jpeg,
                    detail = if (several) R.string.document_format_jpeg_pages else R.string.document_format_jpeg_one,
                    onClick = { onSavePicture(PictureFormat.JPEG) },
                )
                FormatRow(
                    icon = Icons.Filled.PhotoSizeSelectActual,
                    name = R.string.document_format_png,
                    detail = if (several) R.string.document_format_png_pages else R.string.document_format_png_one,
                    onClick = { onSavePicture(PictureFormat.PNG) },
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(CancelPadding),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun FormatRow(icon: ImageVector, name: Int, detail: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(name)) },
        supportingContent = { Text(stringResource(detail)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SaveDialogPreview() {
    LunarisScannerTheme {
        SaveDialog(pageCount = 3, onSavePdf = {}, onSavePicture = {}, onDismiss = {})
    }
}
