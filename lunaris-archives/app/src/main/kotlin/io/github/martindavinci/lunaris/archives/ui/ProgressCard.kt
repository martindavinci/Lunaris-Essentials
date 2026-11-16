package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.ui.theme.iconTile

private val CardMargin = 16.dp
private val CardPadding = 24.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardMaxWidth = 480.dp
private val TileSize = 48.dp
private val TileShape = RoundedCornerShape(14.dp)
private val TitleTop = 20.dp
private val SubtitleTop = 4.dp
private val BarHeight = 8.dp
private val BarShape = RoundedCornerShape(4.dp)

/** The card of frames 11, 12 and 15: what is happening (or has happened), where, and what comes below. */
@Composable
fun ProgressCard(icon: ImageVector, title: AnnotatedString, subtitle: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CardShape, color = colors.surface, modifier = modifier.padding(CardMargin).widthIn(max = CardMaxWidth).fillMaxWidth()) {
        Column(Modifier.padding(CardPadding)) {
            Box(Modifier.size(TileSize).background(colors.iconTile, TileShape), Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.primary)
            }
            Spacer(Modifier.height(TitleTop))
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(SubtitleTop))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            content()
        }
    }
}

/**
 * The 8 dp bar. [progress] below zero means "working, amount unknown". [spoken] is what a screen
 * reader says for it ("31 of 48 files"): the bar alone would be a number without a meaning.
 */
@Composable
fun ThickProgressBar(progress: Float, spoken: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shaped = modifier.fillMaxWidth().height(BarHeight).clip(BarShape)
    if (progress < 0f) {
        LinearProgressIndicator(modifier = shaped.semantics { stateDescription = spoken }, color = colors.primary, trackColor = colors.surfaceVariant)
        return
    }
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        shaped.background(colors.surfaceVariant).semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            stateDescription = spoken
        },
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(BarHeight).background(colors.primary))
    }
}
