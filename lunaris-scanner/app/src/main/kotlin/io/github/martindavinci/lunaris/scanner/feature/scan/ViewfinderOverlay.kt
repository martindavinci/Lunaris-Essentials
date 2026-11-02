package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

/** Fixed colours: they sit on the camera image, whatever the theme. */
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val HintColor = Color.White

private const val SCAN_LINE_MILLIS = 1_600
private val WindowCorner = 20.dp
private val WindowStroke = 3.dp
private val ScanLineStroke = 2.dp
private val ScanLineInset = 12.dp
private val HintTopPadding = 24.dp

/** Clears the torch button in the corner of the camera panel. */
private val HintSidePadding = 72.dp

/** Dims the camera around the window with the moving scan line. Draws only: touches pass through. */
@Composable
internal fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCAN_LINE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan line",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val shape = ViewfinderWindow.of(size.width, size.height)
            val window = Size(shape.width, shape.height)
            val topLeft = Offset((size.width - shape.width) / 2, (size.height - shape.height) / 2)
            val corner = CornerRadius(WindowCorner.toPx())
            drawRect(ScrimColor)
            drawRoundRect(Color.Transparent, topLeft, window, corner, blendMode = BlendMode.Clear)
            drawRoundRect(accent, topLeft, window, corner, style = Stroke(WindowStroke.toPx()))
            val lineY = topLeft.y + shape.height * lineProgress
            val inset = ScanLineInset.toPx()
            drawLine(
                color = accent,
                start = Offset(topLeft.x + inset, lineY),
                end = Offset(topLeft.x + shape.width - inset, lineY),
                strokeWidth = ScanLineStroke.toPx(),
            )
        }
        Text(
            text = stringResource(R.string.scan_hint),
            color = HintColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = HintSidePadding, end = HintSidePadding, top = HintTopPadding),
        )
    }
}
