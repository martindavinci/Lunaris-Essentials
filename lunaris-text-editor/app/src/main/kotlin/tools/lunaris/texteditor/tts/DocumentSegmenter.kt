package tools.lunaris.texteditor.tts

import java.text.BreakIterator
import java.util.Locale

/** Splits the reading text into [Segment]s. Offsets index the same string that is displayed and
 *  spoken, so highlighting and playback stay aligned. Blank units are dropped. */
object DocumentSegmenter {
    fun segment(text: String, style: SegmentStyle, locale: Locale = Locale.getDefault()): List<Segment> =
        when (style) {
            SegmentStyle.SENTENCE -> bySentence(text, locale)
            SegmentStyle.LINE -> byLine(text)
        }

    private fun bySentence(text: String, locale: Locale): List<Segment> {
        val out = ArrayList<Segment>()
        val bi = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            val sub = text.substring(start, end)
            if (sub.isNotBlank()) out.add(Segment(sub, start, end))
            start = end
            end = bi.next()
        }
        return out
    }

    private fun byLine(text: String): List<Segment> {
        val out = ArrayList<Segment>()
        var i = 0
        while (i <= text.length) {
            val nl = text.indexOf('\n', i)
            val end = if (nl == -1) text.length else nl
            val sub = text.substring(i, end)
            if (sub.isNotBlank()) out.add(Segment(sub, i, end))
            if (nl == -1) break
            i = nl + 1
        }
        return out
    }
}
