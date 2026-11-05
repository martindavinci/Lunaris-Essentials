package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PagePoint
import io.github.martindavinci.lunaris.scanner.document.PhotoFit

private val HandleRadius = 11.dp
private val HandleReach = 56.dp
private val EdgeWidth = 2.dp
private val LoupeRadius = 64.dp
private val LoupeMargin = 12.dp
private val CrossArm = 12.dp
private const val LOUPE_ZOOM = 3f
private const val WASH_ALPHA = 0.5f
private const val HALF = 2f

/**
 * The photo with the page outlined over it. A finger hides the corner it moves, so a loupe shows
 * what lies under it, on the side away from the finger.
 */
@Composable
internal fun CornersStage(
    photo: PagePixels,
    corners: PageCorners,
    description: String,
    onMove: (Int, PagePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = rememberPageImage(photo)
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    var stage by remember { mutableStateOf(IntSize.Zero) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    val fit = PhotoFit.of(photo.width, photo.height, stage.width.toFloat().coerceAtLeast(1f), stage.height.toFloat().coerceAtLeast(1f))
    val currentCorners by rememberUpdatedState(corners)
    val currentFit by rememberUpdatedState(fit)
    val currentOnMove by rememberUpdatedState(onMove)

    Canvas(
        modifier
            .onSizeChanged { stage = it }
            .semantics { contentDescription = description }
            // Corners at the sides of the photo must not trigger the system's back gesture.
            .systemGestureExclusion()
            .pointerInput(photo) {
                var grab = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        val inStage = currentCorners.onStage(currentFit, photo)
                        val index = inStage.nearest(start.toPoint(), HandleReach.toPx())
                        dragged = index
                        if (index != null) grab = inStage.points[index].toOffset() - start
                    },
                    onDragEnd = { dragged = null },
                    onDragCancel = { dragged = null },
                    onDrag = { change, _ ->
                        dragged?.let { index ->
                            change.consume()
                            val share = currentFit.toShare((change.position + grab).toPoint(), photo.width, photo.height)
                            currentOnMove(index, share)
                        }
                    },
                )
            },
    ) {
        val inStage = corners.onStage(fit, photo)
        drawPhoto(image, fit)
        drawPageOutline(inStage, fit, photo, accent)
        inStage.points.forEach { point ->
            drawCircle(onAccent, HandleRadius.toPx() + EdgeWidth.toPx(), point.toOffset())
            drawCircle(accent, HandleRadius.toPx(), point.toOffset())
        }
        dragged?.let { index -> drawLoupe(image, fit, inStage.points[index].toOffset(), accent) }
    }
}

private fun PageCorners.onStage(fit: PhotoFit, photo: PagePixels): PageCorners {
    fun place(point: PagePoint) = fit.toStage(point, photo.width, photo.height)
    return PageCorners(place(topLeft), place(topRight), place(bottomRight), place(bottomLeft))
}

private fun Offset.toPoint() = PagePoint(x, y)

private fun PagePoint.toOffset() = Offset(x, y)

private fun DrawScope.drawPhoto(image: ImageBitmap, fit: PhotoFit) {
    withTransform({
        translate(fit.left, fit.top)
        scale(fit.scale, fit.scale, pivot = Offset.Zero)
    }) {
        drawImage(image)
    }
}

private fun DrawScope.drawPageOutline(corners: PageCorners, fit: PhotoFit, photo: PagePixels, accent: Color) {
    val outline = Path().apply {
        corners.points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    val wash = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(
            androidx.compose.ui.geometry.Rect(
                Offset(fit.left, fit.top),
                Size(photo.width * fit.scale, photo.height * fit.scale),
            ),
        )
        addPath(outline)
    }
    drawPath(wash, Color.Black.copy(alpha = WASH_ALPHA))
    drawPath(outline, accent, style = Stroke(EdgeWidth.toPx()))
}

private fun DrawScope.drawLoupe(image: ImageBitmap, fit: PhotoFit, under: Offset, accent: Color) {
    val radius = LoupeRadius.toPx()
    val margin = LoupeMargin.toPx()
    val onTheRight = under.x < size.width / HALF
    val centre = Offset(if (onTheRight) size.width - radius - margin else radius + margin, radius + margin)
    val lens = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(centre, radius))
    }
    clipPath(lens) {
        drawRect(Color.Black)
        withTransform({
            translate(centre.x, centre.y)
            scale(LOUPE_ZOOM * fit.scale, LOUPE_ZOOM * fit.scale, pivot = Offset.Zero)
            translate(-(under.x - fit.left) / fit.scale, -(under.y - fit.top) / fit.scale)
        }) {
            drawImage(image)
        }
    }
    drawCircle(accent, radius, centre, style = Stroke(EdgeWidth.toPx()))
    val arm = CrossArm.toPx()
    drawLine(accent, centre - Offset(arm, 0f), centre + Offset(arm, 0f), EdgeWidth.toPx())
    drawLine(accent, centre - Offset(0f, arm), centre + Offset(0f, arm), EdgeWidth.toPx())
}
