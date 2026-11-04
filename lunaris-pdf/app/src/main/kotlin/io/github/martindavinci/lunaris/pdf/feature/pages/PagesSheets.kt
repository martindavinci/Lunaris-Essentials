package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.pages.InsertChoices
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import io.github.martindavinci.lunaris.pdf.ui.theme.iconTile

private const val SCRIM_ALPHA = 0.62f
private val PanelPadding = 24.dp
private val RowSpacing = 16.dp
private val TileSize = 44.dp
private val TileShape = RoundedCornerShape(12.dp)
private val ChooserShape = RoundedCornerShape(12.dp)
private val ChooserPadding = 12.dp
private val ChooserHeight = 44.dp
private val RowPadding = 10.dp
private val DialogShape = RoundedCornerShape(28.dp)
private val DialogButtonHeight = 48.dp
private val DialogButtonShape = RoundedCornerShape(24.dp)
private val DialogSpacing = 12.dp

/** The three kinds of new page, in one panel. */
@Composable
internal fun AddPagesPanel(
    panel: PagesPanel.Add,
    pageCount: Int,
    onBlank: () -> Unit,
    onOtherPdf: () -> Unit,
    onImages: () -> Unit,
    onAction: (PagesAction) -> Unit,
) {
    BottomPanel(onDismiss = { onAction(PagesAction.ClosePanel) }) {
        Text(stringResource(R.string.pages_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = PanelPadding))
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = RowSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DialogSpacing),
        ) {
            Text(stringResource(R.string.pages_insert_after), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InsertChooser(panel.insertAfter, pageCount) { onAction(PagesAction.SetInsertAfter(it)) }
        }
        val neighbour = panel.insertAfter
        KindRow(
            icon = Icons.Filled.NoteAdd,
            title = stringResource(R.string.pages_add_blank),
            detail = if (neighbour > 0) stringResource(R.string.pages_add_blank_detail, neighbour) else stringResource(R.string.pages_add_blank_first),
            onClick = onBlank,
        )
        KindRow(Icons.Filled.PictureAsPdf, stringResource(R.string.pages_add_pdf), stringResource(R.string.pages_add_pdf_detail), onOtherPdf)
        KindRow(Icons.Filled.AddPhotoAlternate, stringResource(R.string.pages_add_image), stringResource(R.string.pages_add_image_detail), onImages)
        Box(Modifier.size(RowSpacing))
    }
}

@Composable
private fun InsertChooser(after: Int, pageCount: Int, onChoose: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = ChooserShape,
            border = BorderStroke(1.dp, colors.outline),
            modifier = Modifier.heightIn(min = ChooserHeight),
        ) {
            Text(placeLabel(after), color = colors.onSurface)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            InsertChoices.around(after, pageCount).forEach { place ->
                DropdownMenuItem(
                    text = { Text(placeLabel(place)) },
                    onClick = {
                        open = false
                        onChoose(place)
                    },
                )
            }
        }
    }
}

@Composable
private fun placeLabel(place: Int): String =
    if (place == 0) stringResource(R.string.pages_insert_start) else stringResource(R.string.pages_page_label, place)

@Composable
private fun KindRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PanelPadding, vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Box(Modifier.size(TileSize).clip(TileShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Buttons one under the other, so that long words in any language stay whole. */
@Composable
internal fun LeaveDialog(changes: Int, onSave: () -> Unit, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)).clickable { onAction(PagesAction.KeepEditing) }.padding(PanelPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = DialogShape, color = colors.surface, modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            Column(
                modifier = Modifier.padding(PanelPadding).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DialogSpacing),
            ) {
                Text(stringResource(R.string.pages_leave_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    text = pluralStringResource(R.plurals.pages_leave_message, changes, changes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onSave,
                    shape = DialogButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth().heightIn(min = DialogButtonHeight),
                ) {
                    Text(stringResource(R.string.pages_leave_save), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { onAction(PagesAction.DiscardAndLeave) },
                    shape = DialogButtonShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = DialogButtonHeight),
                ) {
                    Text(stringResource(R.string.pages_leave_discard), color = colors.onSurface, textAlign = TextAlign.Center)
                }
                TextButton(onClick = { onAction(PagesAction.KeepEditing) }) { Text(stringResource(R.string.pages_leave_keep)) }
            }
        }
    }
}
