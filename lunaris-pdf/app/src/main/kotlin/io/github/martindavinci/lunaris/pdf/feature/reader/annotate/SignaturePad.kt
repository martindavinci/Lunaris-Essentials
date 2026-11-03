package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.annotate.SignatureStrokes
import io.github.martindavinci.lunaris.pdf.annotate.StrokePoint
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import java.io.ByteArrayOutputStream

private const val PNG_QUALITY = 100
private val PadHeight = 200.dp
private val PadShape = RoundedCornerShape(16.dp)
private val PanelPadding = 20.dp
private val PanelSpacing = 12.dp
private val InkWidth = 3.dp
private val InkColor = Color(0xFF14143C)

@Composable
internal fun SignaturePad(onDone: (ByteArray) -> Unit, onCancel: () -> Unit) {
    var strokes by remember { mutableStateOf(emptyList<List<StrokePoint>>()) }
    val inkPx = with(LocalDensity.current) { InkWidth.toPx() }
    BottomPanel(onDismiss = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
            verticalArrangement = Arrangement.spacedBy(PanelSpacing),
        ) {
            Text(stringResource(R.string.annotate_draw_title), style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(PadHeight)
                    .background(Color.White, PadShape)
                    .pointerInput(Unit) {
                        // From the first touch: a drag gesture would begin only after the finger has moved a little.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            strokes = strokes + listOf(listOf(down.position.asPoint()))
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.id == down.id }?.let { change ->
                                    change.consume()
                                    strokes = strokes.dropLast(1) + listOf(strokes.last() + change.position.asPoint())
                                }
                            } while (event.changes.any { it.id == down.id && it.pressed })
                        }
                    },
            ) {
                strokes.forEach { stroke ->
                    drawPath(stroke.asPath(), InkColor, style = Stroke(inkPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PanelSpacing)) {
                TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) {
                    Text(stringResource(R.string.annotate_draw_clear))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.annotate_draw_cancel)) }
                Button(
                    onClick = { signaturePng(strokes, inkPx)?.let(onDone) },
                    enabled = strokes.isNotEmpty(),
                ) { Text(stringResource(R.string.annotate_save)) }
            }
        }
    }
}

private fun Offset.asPoint() = StrokePoint(x, y)

private fun List<StrokePoint>.asPath() = Path().also { path ->
    firstOrNull()?.let { path.moveTo(it.x, it.y) }
    drop(1).forEach { path.lineTo(it.x, it.y) }
}

/** Only the area of the ink, on nothing: on the page it is the ink that shows. */
private fun signaturePng(strokes: List<List<StrokePoint>>, inkPx: Float): ByteArray? {
    val bounds = SignatureStrokes.bounds(strokes, margin = inkPx) ?: return null
    val bitmap = Bitmap.createBitmap(
        (bounds.right - bounds.left).toInt().coerceAtLeast(1),
        (bounds.bottom - bounds.top).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.translate(-bounds.left, -bounds.top)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = inkPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.rgb(InkColor.red.times(255).toInt(), InkColor.green.times(255).toInt(), InkColor.blue.times(255).toInt())
    }
    strokes.forEach { stroke ->
        val path = android.graphics.Path()
        stroke.firstOrNull()?.let { path.moveTo(it.x, it.y) }
        stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    bitmap.recycle()
    return out.toByteArray()
}
