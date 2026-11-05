package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.document.DocumentPage

private val BarPadding = 16.dp
private val ShutterSize = 72.dp
private val ShutterRing = 4.dp
private val ShutterGap = 6.dp
private val StackWidth = 40.dp
private val StackHeight = 56.dp
private val BadgeSize = 20.dp
private val BadgeOffset = 8.dp
private const val DISABLED_ALPHA = 0.4f

/** The pages so far, the shutter and the way on. Across the bottom, or down the side when [sideways]. */
@Composable
internal fun CameraControls(
    pages: List<DocumentPage>,
    shutterEnabled: Boolean,
    sideways: Boolean,
    onShutter: () -> Unit,
    onFinish: () -> Unit,
) {
    val background = Modifier.zIndex(1f).background(MaterialTheme.colorScheme.background)
    val stack = @Composable { if (pages.isNotEmpty()) PageStack(pages, onFinish) }
    val finish = @Composable {
        if (pages.isNotEmpty()) Button(onClick = onFinish) { Text(stringResource(R.string.document_finish)) }
    }
    if (sideways) {
        Column(
            modifier = background.fillMaxHeight().padding(BarPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { finish() }
            Shutter(shutterEnabled, onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { stack() }
        }
    } else {
        Row(
            modifier = background.fillMaxWidth().padding(BarPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { stack() }
            Shutter(shutterEnabled, onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { finish() }
        }
    }
}

/** The last page taken, with how many there are. */
@Composable
private fun PageStack(pages: List<DocumentPage>, onClick: () -> Unit) {
    val description = stringResource(R.string.document_page_of, pages.size, pages.size)
    Box(Modifier.size(StackWidth, StackHeight).clickable(onClickLabel = description, onClick = onClick)) {
        val thumbnail = pages.last().thumbnail
        if (thumbnail != null) {
            Image(rememberPageImage(thumbnail), description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(BadgeOffset, -BadgeOffset)
                .size(BadgeSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${pages.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = DISABLED_ALPHA)
    Box(
        modifier = Modifier
            .size(ShutterSize)
            .border(ShutterRing, tint, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.document_take_photo), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(ShutterSize - (ShutterRing + ShutterGap) * 2).background(tint, CircleShape))
    }
}
