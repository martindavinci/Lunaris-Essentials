package io.github.martindavinci.lunaris.scanner.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.CornersProblem
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val StagePadding = 24.dp
private val BarPadding = 12.dp
private val HintPadding = 16.dp

@Composable
internal fun rememberPageImage(page: PagePixels): ImageBitmap = remember(page) {
    Bitmap.createBitmap(page.pixels, page.width, page.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
internal fun CornersScreen(state: DocumentUiState, onAction: (DocumentAction) -> Unit) {
    val problem = state.cornersProblem
    Column(Modifier.fillMaxSize()) {
        DocumentTopBar(stringResource(R.string.document_corners_title), onBack = { onAction(DocumentAction.BackPressed) })
        Text(
            text = stringResource(cornersMessage(problem)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HintPadding),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(StagePadding), contentAlignment = Alignment.Center) {
            val photo = state.photo
            if (photo == null) {
                CircularProgressIndicator()
            } else {
                CornersStage(
                    photo = photo,
                    corners = state.corners,
                    description = stringResource(R.string.document_corners_hint),
                    onMove = { index, to -> onAction(DocumentAction.CornerMoved(index, to)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(BarPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(DocumentAction.CornersReset) }) {
                Text(stringResource(R.string.document_corners_reset))
            }
            Button(
                onClick = { onAction(DocumentAction.CornersDone) },
                enabled = problem == null && state.photo != null,
            ) {
                Text(stringResource(R.string.document_next))
            }
        }
    }
}

private fun cornersMessage(problem: CornersProblem?): Int = when (problem) {
    null -> R.string.document_corners_hint
    CornersProblem.CROSSED -> R.string.document_corners_crossed
    CornersProblem.EDGE_CROSSED -> R.string.document_corners_edge_crossed
    CornersProblem.TOO_SMALL -> R.string.document_corners_too_small
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CornersScreenPreview() {
    LunarisScannerTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            CornersScreen(
                state = DocumentUiState(photo = PagePixels(IntArray(12) { -1 }, 3, 4)),
                onAction = {},
            )
        }
    }
}
