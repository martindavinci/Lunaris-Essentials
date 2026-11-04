package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val PAPER_ASPECT = 1f / 1.414f
private const val SELECTED_SCALE = 0.92f
private const val DIMMED_ALPHA = 0.55f
private const val HALF_TURN = 180
private const val MARK_ALPHA = 0.6f
private const val GAP_NUMBER_ALPHA = 0.5f
private val Paper = Color.White
private val PaperEdge = Color(0x33000000)
private val TileShape = RoundedCornerShape(6.dp)
private val RingWidth = 3.dp
private val HairWidth = 1.dp
private val MarkSize = 24.dp
private val MarkInset = 6.dp
private val NumberGap = 4.dp
private val GapCorner = 6.dp
private val GapStroke = 2.dp
private val DashOn = 8.dp
private val DashOff = 6.dp
private val StackStep = 4.dp
private val StackShadow = 8.dp

/** A page in the grid: its picture turned as the page now stands, its number below. */
@Composable
internal fun PageTile(
    number: Int,
    turn: Int,
    /** The size of a page with no picture, which is a blank one. */
    blankAspect: Float?,
    selected: Boolean,
    selecting: Boolean,
    dimmed: Boolean,
    key: Any,
    load: suspend () -> ImageBitmap?,
    modifier: Modifier = Modifier,
    showNumber: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val picture by produceState<ImageBitmap?>(null, key) { value = load() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NumberGap)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(PAPER_ASPECT), contentAlignment = Alignment.Center) {
            val image = picture
            val own = image?.let { it.width.toFloat() / it.height } ?: blankAspect
            val aspect = own?.let { if (turn % HALF_TURN != 0) 1f / it else it }
            val scale = if (selected) SELECTED_SCALE else 1f
            Box(pageSize(aspect, maxWidth, maxHeight).scale(scale).alpha(if (dimmed) DIMMED_ALPHA else 1f)) {
                val background = if (own == null) colors.surfaceVariant else Paper
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(TileShape)
                        .background(background)
                        .border(BorderStroke(if (selected) RingWidth else HairWidth, if (selected) colors.primary else PaperEdge), TileShape),
                ) {
                    if (image != null) PagePicture(image, turn)
                }
                if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopEnd))
            }
        }
        if (showNumber) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

private fun pageSize(aspect: Float?, maxWidth: Dp, maxHeight: Dp): Modifier {
    if (aspect == null) return Modifier.fillMaxSize()
    val width = minOf(maxWidth.value, maxHeight.value * aspect)
    return Modifier.size(width.dp, (width / aspect).dp)
}

@Composable
private fun PagePicture(image: ImageBitmap, turn: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val sideways = turn % HALF_TURN != 0
        val width = if (sideways) size.height else size.width
        val height = if (sideways) size.width else size.height
        rotate(turn.toFloat()) {
            drawImage(
                image = image,
                dstOffset = IntOffset(
                    (center.x - width / 2).roundToInt(),
                    (center.y - height / 2).roundToInt(),
                ),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            )
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .offset(x = -MarkInset / 2, y = MarkInset / 2)
            .size(MarkSize)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Paper.copy(alpha = MARK_ALPHA))
            .border(BorderStroke(HairWidth, if (selected) colors.primary else colors.outline), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary)
    }
}

/** The place a carried page leaves, marked until it lands. */
@Composable
internal fun PageGap(number: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NumberGap)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(PAPER_ASPECT).drawBehind {
                drawRoundRect(
                    color = colors.outline,
                    cornerRadius = CornerRadius(GapCorner.toPx()),
                    style = Stroke(width = GapStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
                )
            },
        )
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant.copy(alpha = GAP_NUMBER_ALPHA),
        )
    }
}

/** What dragging shows under the finger: the page on a stack, with how many go with it. */
@Composable
internal fun DragStack(count: Int, content: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        if (count > 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = StackStep, y = StackStep)
                    .clip(TileShape)
                    .background(Paper)
                    .border(BorderStroke(HairWidth, colors.outline), TileShape),
            )
        }
        Box(Modifier.shadow(StackShadow, TileShape)) { content() }
        if (count > 1) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = MarkInset, y = -MarkInset)
                    .size(MarkSize)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer)
            }
        }
    }
}

