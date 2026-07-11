package tools.lunaris.texteditor.tts

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class DocumentSegmenterTest {
    private fun seg(text: String, style: SegmentStyle) =
        DocumentSegmenter.segment(text, style, Locale.ENGLISH)

    @Test fun sentences_splitOnPunctuation() {
        val text = "One. Two? Three!"
        val out = seg(text, SegmentStyle.SENTENCE)
        assertThat(out.map { it.text.trim() }).containsExactly("One.", "Two?", "Three!").inOrder()
    }

    @Test fun sentences_offsetsReconstruct() {
        val text = "Alpha beta. Gamma delta."
        seg(text, SegmentStyle.SENTENCE).forEach {
            assertThat(text.substring(it.start, it.end)).isEqualTo(it.text)
        }
    }

    @Test fun lines_splitAndSkipBlank() {
        val text = "first\n\n  \nsecond"
        val out = seg(text, SegmentStyle.LINE)
        assertThat(out.map { it.text }).containsExactly("first", "second").inOrder()
        assertThat(text.substring(out[1].start, out[1].end)).isEqualTo("second")
    }

    @Test fun empty_isEmpty() {
        assertThat(seg("", SegmentStyle.SENTENCE)).isEmpty()
        assertThat(seg("   \n  ", SegmentStyle.LINE)).isEmpty()
    }
}
