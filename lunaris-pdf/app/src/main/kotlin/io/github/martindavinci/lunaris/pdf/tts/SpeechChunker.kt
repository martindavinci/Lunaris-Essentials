package io.github.martindavinci.lunaris.pdf.tts

/**
 * Splits text into pieces no longer than [maxPiece] characters. `TextToSpeech.speak()` rejects any
 * single string at/above its engine limit (`getMaxSpeechInputLength()`, ~4000), so a long document
 * must be spoken as several queued utterances. Breaks at the last whitespace before the limit to
 * avoid cutting a word; a single unbroken run longer than the limit is hard-cut. Lossless: the
 * pieces concatenate back to the original text.
 */
object SpeechChunker {
    fun chunk(text: String, maxPiece: Int): List<String> {
        require(maxPiece > 0) { "maxPiece must be positive" }
        if (text.length <= maxPiece) return listOf(text)
        val out = ArrayList<String>(text.length / maxPiece + 1)
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxPiece, text.length)
            if (end < text.length) {
                // Back up to the last space/newline in the window so words stay intact.
                val ws = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('\n', end - 1))
                if (ws > start) end = ws + 1
            }
            out.add(text.substring(start, end))
            start = end
        }
        return out
    }
}
