package io.github.martindavinci.lunaris.scanner.feature.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val PreviewMaxWidth = 320.dp
private val PreviewPadding = 12.dp
private val PlaceholderPadding = 48.dp

/**
 * The code on a white card, in light and dark theme alike. The image is encoded for the measured
 * [sizePx] and drawn one to one without filtering, so modules keep their edges. Only a code with
 * more modules than the card has pixels is scaled down, smoothly, to keep it recognizable.
 */
@Composable
internal fun CodePreview(
    image: CodeImage?,
    sizePx: Int,
    onSized: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .widthIn(max = PreviewMaxWidth)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (image != null) Color.White else MaterialTheme.colorScheme.surfaceVariant)
            .padding(PreviewPadding)
            .onSizeChanged { onSized(it.width) },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            val bitmap = remember(image) { image.toBitmap().asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.create_preview_description),
                contentScale = ContentScale.Inside,
                filterQuality = if (image.width > sizePx) FilterQuality.Low else FilterQuality.None,
            )
        } else {
            Text(
                stringResource(R.string.create_preview_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = PlaceholderPadding),
            )
        }
    }
}
