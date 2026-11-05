package io.github.martindavinci.lunaris.scanner.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.martindavinci.lunaris.scanner.feature.ScreenTopBar
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val StagePadding = 24.dp
private val BarPadding = 12.dp
private val BannerPadding = 12.dp
private val BannerMargin = 16.dp
private val BannerSpacing = 12.dp
private val ButtonSpacing = 8.dp
private val ButtonIconSpacing = 8.dp

@Composable
internal fun rememberPageImage(page: PagePixels): ImageBitmap = remember(page) {
    Bitmap.createBitmap(page.pixels, page.width, page.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** A page just taken can be retaken, or be followed by another. One being edited from the review is already part of the scan. */
@Composable
internal fun CornersScreen(state: DocumentUiState, onAction: (DocumentAction) -> Unit) {
    val problem = state.cornersProblem
    val usable = problem == null && state.photo != null
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = stringResource(R.string.document_page_number, state.current + 1),
            subtitle = stringResource(if (problem == null && state.currentPage?.found != null) R.string.document_corners_found else R.string.document_corners_to_fix),
            onBack = { onAction(DocumentAction.BackPressed) },
            actions = {
                TextButton(onClick = { onAction(DocumentAction.CornersReset) }) {
                    Text(stringResource(R.string.document_corners_reset))
                }
            },
        )
        val banner = bannerMessage(problem, state.cornersNotFound)
        if (banner != null) CornersBanner(banner)
        Box(Modifier.weight(1f).fillMaxWidth().padding(StagePadding), contentAlignment = Alignment.Center) {
            val photo = state.photo
            if (photo == null) {
                CircularProgressIndicator()
            } else {
                CornersStage(
                    photo = photo,
                    corners = state.corners,
                    faulty = problem != null,
                    description = stringResource(R.string.document_corners_hint),
                    onMove = { index, to -> onAction(DocumentAction.CornerMoved(index, to)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(BarPadding),
            horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.newPage) {
                TextButton(onClick = { onAction(DocumentAction.RetakePhoto) }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(ButtonIconSpacing))
                    Text(stringResource(R.string.document_retake), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.newPage) {
                FilledTonalButton(onClick = { onAction(DocumentAction.AnotherPage) }, enabled = usable) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(ButtonIconSpacing))
                    Text(stringResource(R.string.document_another_page), maxLines = 1)
                }
            }
            Button(onClick = { onAction(DocumentAction.CornersDone) }, enabled = usable) {
                Text(stringResource(R.string.document_next), maxLines = 1)
            }
        }
    }
}

private fun bannerMessage(problem: CornersProblem?, notFound: Boolean): Int? = when {
    problem == CornersProblem.CROSSED -> R.string.document_corners_crossed
    problem == CornersProblem.EDGE_CROSSED -> R.string.document_corners_edge_crossed
    problem == CornersProblem.TOO_SMALL -> R.string.document_corners_too_small
    notFound -> R.string.document_corners_not_found
    else -> null
}

@Composable
private fun CornersBanner(message: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = BannerMargin),
    ) {
        Row(
            modifier = Modifier.padding(BannerPadding),
            horizontalArrangement = Arrangement.spacedBy(BannerSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CornersScreenPreview() {
    LunarisScannerTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            CornersScreen(
                state = DocumentUiState(step = DocumentStep.CORNERS, newPage = true, photo = PagePixels(IntArray(12) { -1 }, 3, 4)),
                onAction = {},
            )
        }
    }
}
