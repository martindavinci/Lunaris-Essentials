package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.convert.FileKind

private val RowPadding = 8.dp
private val RowSpacing = 12.dp
private val ThumbWidth = 44.dp
private val ThumbHeight = 56.dp
private val ThumbShape = RoundedCornerShape(4.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
private val BadgePadding = 3.dp
private val MinRowHeight = 64.dp
private val Paper = Color.White

/** One file of the list. Only the handle drags: the rest of the row stays free for scrolling. */
@Composable
internal fun CreateRow(
    item: CreateItem,
    thumbnail: suspend (CreateItem) -> ImageBitmap?,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MinRowHeight).padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Box(
            Modifier.size(ThumbWidth).pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        RowThumb(item, thumbnail)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = rowMeta(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.problem != null) colors.error else colors.onSurfaceVariant,
            )
        }
        if (item.problem != null) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.create_remove)) }
        } else {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.create_remove), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowThumb(item: CreateItem, thumbnail: suspend (CreateItem) -> ImageBitmap?) {
    val colors = MaterialTheme.colorScheme
    val problem = item.problem
    val picture by produceState<ImageBitmap?>(null, item.id) { value = thumbnail(item) }
    Box(
        Modifier
            .size(ThumbWidth, ThumbHeight)
            .clip(ThumbShape)
            .background(if (problem != null) colors.errorContainer else Paper),
        contentAlignment = Alignment.Center,
    ) {
        val image = picture
        when {
            problem != null -> Icon(
                when (problem) {
                    FileProblem.PROTECTED -> Icons.Filled.Lock
                    FileProblem.DAMAGED -> Icons.Filled.BrokenImage
                    FileProblem.UNSUPPORTED -> Icons.Filled.Block
                },
                contentDescription = null,
                tint = colors.onErrorContainer,
            )
            image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        badge(item.kind)?.let { label ->
            Surface(shape = BadgeShape, color = colors.primaryContainer, modifier = Modifier.align(Alignment.BottomStart).padding(BadgePadding)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = BadgePadding),
                )
            }
        }
    }
}

private fun badge(kind: FileKind): String? = when (kind) {
    FileKind.MARKDOWN -> "MD"
    FileKind.HTML -> "HTML"
    FileKind.TEXT -> "TXT"
    else -> null
}

@Composable
private fun rowMeta(item: CreateItem): String = when (item.problem) {
    FileProblem.PROTECTED -> stringResource(R.string.create_problem_protected)
    FileProblem.DAMAGED -> stringResource(R.string.create_problem_damaged)
    FileProblem.UNSUPPORTED -> stringResource(R.string.create_problem_unsupported)
    null -> when (item.kind) {
        FileKind.PDF -> stringResource(R.string.create_meta_pdf, pluralStringResource(R.plurals.pages_split_count, item.pages, item.pages))
        FileKind.IMAGE -> stringResource(R.string.create_meta_image)
        FileKind.MARKDOWN -> stringResource(R.string.create_meta_markdown, pluralStringResource(R.plurals.create_about_pages, item.pages, item.pages))
        FileKind.HTML -> stringResource(R.string.create_meta_html, pluralStringResource(R.plurals.create_about_pages, item.pages, item.pages))
        else -> stringResource(R.string.create_meta_text, pluralStringResource(R.plurals.create_about_pages, item.pages, item.pages))
    }
}
