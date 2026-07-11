package tools.lunaris.texteditor.tts

/** A span of the displayed reading text: [text] == displayString.substring([start], [end]). */
data class Segment(val text: String, val start: Int, val end: Int)

/** How a document is broken into readable units: prose by sentence, code/HTML by line. */
enum class SegmentStyle { SENTENCE, LINE }
