package tools.lunaris.texteditor.edit

data class TextStats(val words: Int, val characters: Int) {
    companion object {
        fun of(text: String): TextStats {
            val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
            return TextStats(words, text.length)
        }
    }
}
