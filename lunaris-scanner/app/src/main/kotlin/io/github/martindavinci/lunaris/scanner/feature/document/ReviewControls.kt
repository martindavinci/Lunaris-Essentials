package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.PaperSize
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.feature.create.ChoiceChips

private val Spacing = 8.dp
private val ThumbnailSize = 56.dp
private val ThumbnailShape = RoundedCornerShape(6.dp)
private val SelectedBorder = 2.dp
private val UnselectedBorder = 1.dp

@Composable
internal fun ReviewControls(
    state: DocumentUiState,
    onAction: (DocumentAction) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onSavePdf: () -> Unit,
    onSavePicture: (PictureFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.currentPage?.settings ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing)) {
        PageTools(state, onAction)
        ChoiceChips(
            options = CleanMode.entries,
            selected = settings.clean,
            label = ::cleanLabel,
            onSelect = { onAction(DocumentAction.CleanChosen(it)) },
        )
        ChoiceChips(
            options = PaperSize.entries,
            selected = settings.paper,
            label = ::paperLabel,
            onSelect = { onAction(DocumentAction.PaperChosen(it)) },
        )
        PageStrip(state, onAction, onPickImage, onTakePhoto)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            OutlinedButton(onClick = { onAction(DocumentAction.SharePdf) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(Spacing))
                Text(stringResource(R.string.document_share))
            }
            SaveButton(state.pages.size, onSavePdf, onSavePicture, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PageTools(state: DocumentUiState, onAction: (DocumentAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Tool(Icons.Filled.Crop, R.string.document_corners_title) { onAction(DocumentAction.EditCorners) }
        Tool(Icons.AutoMirrored.Filled.RotateLeft, R.string.document_turn_left) { onAction(DocumentAction.TurnLeft) }
        Tool(Icons.AutoMirrored.Filled.RotateRight, R.string.document_turn_right) { onAction(DocumentAction.TurnRight) }
        Tool(Icons.Filled.ChevronLeft, R.string.document_move_earlier, enabled = state.current > 0) {
            onAction(DocumentAction.MovePageEarlier)
        }
        Tool(Icons.Filled.ChevronRight, R.string.document_move_later, enabled = state.current < state.pages.size - 1) {
            onAction(DocumentAction.MovePageLater)
        }
        Tool(Icons.Filled.Delete, R.string.document_delete_page) { onAction(DocumentAction.DeletePage) }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun PageStrip(
    state: DocumentUiState,
    onAction: (DocumentAction) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.pages.forEachIndexed { index, page ->
            PageThumbnail(page, index, selected = index == state.current) { onAction(DocumentAction.PageSelected(index)) }
        }
        AddPageTile(onPickImage, onTakePhoto)
    }
}

@Composable
private fun PageThumbnail(page: DocumentPage, index: Int, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) {
        BorderStroke(SelectedBorder, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(UnselectedBorder, MaterialTheme.colorScheme.outlineVariant)
    }
    val description = stringResource(R.string.document_page_number, index + 1)
    Box(
        modifier = Modifier
            .size(ThumbnailSize)
            .clip(ThumbnailShape)
            .border(border, ThumbnailShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = page.thumbnail
        if (thumbnail != null) {
            Image(rememberPageImage(thumbnail), description, contentScale = ContentScale.Fit, modifier = Modifier.size(ThumbnailSize))
        } else {
            Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AddPageTile(onPickImage: () -> Unit, onTakePhoto: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing))
            Text(stringResource(R.string.document_add_page))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_take_photo)) },
                onClick = {
                    open = false
                    onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.document_pick_image)) },
                onClick = {
                    open = false
                    onPickImage()
                },
            )
        }
    }
}

private fun cleanLabel(mode: CleanMode): Int = when (mode) {
    CleanMode.COLOUR -> R.string.document_clean_colour
    CleanMode.GREY -> R.string.document_clean_grey
    CleanMode.BLACK_AND_WHITE -> R.string.document_clean_black_white
    CleanMode.NONE -> R.string.document_clean_none
}

private fun paperLabel(paper: PaperSize): Int = when (paper) {
    PaperSize.AS_PHOTOGRAPHED -> R.string.document_paper_photographed
    PaperSize.A4 -> R.string.document_paper_a4
    PaperSize.LETTER -> R.string.document_paper_letter
}
