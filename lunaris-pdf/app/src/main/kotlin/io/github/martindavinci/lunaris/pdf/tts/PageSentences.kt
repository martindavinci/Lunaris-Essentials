package io.github.martindavinci.lunaris.pdf.tts

/** The text of a page as sentences to speak, without the line breaks of its layout. */
object PageSentences {
    private val CUT_WORD = Regex("""-\s*\n\s*""")
    private val WHITE_SPACE = Regex("""\s+""")
    private val SENTENCE_END = Regex("""(?<=[.!?\u2026])\s+|(?<=[\u3002\uFF01\uFF1F])\s*""")

    fun of(text: String): List<String> = text
        .replace(CUT_WORD, "")
        .replace(WHITE_SPACE, " ")
        .trim()
        .split(SENTENCE_END)
        .filter { it.isNotBlank() }
}
