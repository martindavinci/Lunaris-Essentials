package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.PictureFormat

/** A PDF holds every page in one file. A picture holds one, so several pages make several files. */
@Composable
internal fun SaveButton(
    pageCount: Int,
    onSavePdf: () -> Unit,
    onSavePicture: (PictureFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.document_save))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_format_pdf)) },
                onClick = {
                    open = false
                    onSavePdf()
                },
            )
            PictureItem(R.string.document_format_jpeg, pageCount) {
                open = false
                onSavePicture(PictureFormat.JPEG)
            }
            PictureItem(R.string.document_format_png, pageCount) {
                open = false
                onSavePicture(PictureFormat.PNG)
            }
        }
    }
}

@Composable
private fun PictureItem(format: Int, pageCount: Int, onClick: () -> Unit) {
    val name = stringResource(format)
    DropdownMenuItem(
        text = { Text(if (pageCount > 1) stringResource(R.string.document_format_per_page, name) else name) },
        onClick = onClick,
    )
}
