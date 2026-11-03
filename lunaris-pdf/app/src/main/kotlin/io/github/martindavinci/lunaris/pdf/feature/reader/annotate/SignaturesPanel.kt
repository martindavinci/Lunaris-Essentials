package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.annotate.SavedSignature
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PanelPadding = 20.dp
private val PanelSpacing = 12.dp
private val MaxListHeight = 320.dp
private val CardShape = RoundedCornerShape(16.dp)
private val CardHeight = 88.dp
private val CardPadding = 12.dp

/** Signatures stay on this phone. White cards, as the ink is dark whatever the theme. */
@Composable
internal fun SignaturesPanel(signatures: List<SavedSignature>, onAction: (ReaderAction) -> Unit) {
    var drawing by rememberSaveable { mutableStateOf(false) }
    if (drawing) {
        SignaturePad(
            onDone = { png ->
                drawing = false
                onAction(EditAction.AddSignature(png))
            },
            onCancel = { drawing = false },
        )
        return
    }
    BottomPanel(onDismiss = { onAction(EditAction.CloseSignatures) }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.annotate_signatures), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { drawing = true }) { Text(stringResource(R.string.annotate_draw_new)) }
            }
            if (signatures.isEmpty()) {
                Text(stringResource(R.string.annotate_signatures_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.annotate_signatures_how), style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier.heightIn(max = MaxListHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PanelSpacing),
                ) {
                    signatures.forEachIndexed { index, signature -> SignatureCard(index + 1, signature, onAction) }
                }
            }
            Text(
                stringResource(R.string.annotate_signatures_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignatureCard(number: Int, signature: SavedSignature, onAction: (ReaderAction) -> Unit) {
    val picture by produceState<ImageBitmap?>(null, signature.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(signature.path)?.asImageBitmap() }
    }
    Surface(
        onClick = { onAction(EditAction.ChooseSignature(signature.id)) },
        shape = CardShape,
        color = Color.White,
    ) {
        Box(Modifier.fillMaxWidth().height(CardHeight).background(Color.White).padding(CardPadding)) {
            picture?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.annotate_signature_item, number),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth().height(CardHeight),
                )
            }
            IconButton(
                onClick = { onAction(EditAction.RemoveSignature(signature.id)) },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.annotate_delete_signature), tint = Color.DarkGray)
            }
        }
    }
}
