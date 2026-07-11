package tools.lunaris.texteditor.feature.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.tts.Segment

/** Full-screen reading view: the spoken text with the current segment highlighted, auto-scrolled. */
@Composable
fun ReadingPane(
    text: String,
    segments: List<Segment>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val current = segments.getOrNull(currentIndex)

    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = DIM_ALPHA)
    val strong = MaterialTheme.colorScheme.onSurface
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA)

    val annotated: AnnotatedString = remember(text, current) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = dim)) { append(text) }
            if (current != null) {
                addStyle(SpanStyle(color = strong, background = highlightBg), current.start, current.end)
            }
        }
    }

    // Scroll the current segment's top into view whenever it changes.
    LaunchedEffect(currentIndex, layout) {
        val result = layout ?: return@LaunchedEffect
        val start = current?.start ?: return@LaunchedEffect
        val line = result.getLineForOffset(start)
        scroll.animateScrollTo(result.getLineTop(line).toInt())
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it },
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(ContentPadding),
    )
}

private const val DIM_ALPHA = 0.45f
private const val HIGHLIGHT_ALPHA = 0.25f
private val ContentPadding = 16.dp

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ReadingPanePreview() {
    tools.lunaris.texteditor.ui.theme.LunarisEditorTheme {
        val text = "One sentence here. The second sentence is highlighted. And a third."
        ReadingPane(
            text = text,
            segments = listOf(
                Segment("One sentence here.", 0, 18),
                Segment(" The second sentence is highlighted.", 18, 54),
                Segment(" And a third.", 54, text.length),
            ),
            currentIndex = 1,
        )
    }
}
