package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry

private const val PLACEHOLDER_LINES = 7
private const val PLACEHOLDER_FIRST_LINE = 0.12f
private const val PLACEHOLDER_LINE_GAP = 0.11f
private const val PLACEHOLDER_MARGIN = 0.14f
private val PlaceholderLine = Color(0xFFC6CBD2)
private val PaperColor = Color.White
private val ThumbShape = RoundedCornerShape(4.dp)

/**
 * The first page, or a sketch of a page until there is a picture of it. A file with a password never
 * has one: its first page stays behind the password.
 */
@Composable
internal fun DocumentThumb(entry: RecentEntry, thumbnail: suspend (String) -> ImageBitmap?, width: Dp, height: Dp) {
    val picture by produceState<ImageBitmap?>(null, entry.uri) { value = if (entry.locked) null else thumbnail(entry.uri) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(ThumbShape)
            .background(if (entry.locked) colors.surfaceVariant else PaperColor),
        contentAlignment = Alignment.Center,
    ) {
        val image = picture
        when {
            image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            entry.locked -> Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.onSurfaceVariant)
            else -> Box(Modifier.fillMaxSize().drawBehind { sketchOfAPage() })
        }
    }
}

private fun DrawScope.sketchOfAPage() {
    val margin = size.width * PLACEHOLDER_MARGIN
    val line = size.height * PLACEHOLDER_LINE_GAP / 2
    repeat(PLACEHOLDER_LINES) { index ->
        drawRect(
            color = PlaceholderLine,
            topLeft = Offset(margin, size.height * (PLACEHOLDER_FIRST_LINE + PLACEHOLDER_LINE_GAP * index)),
            size = Size(size.width - margin * 2, line / 2),
        )
    }
}
