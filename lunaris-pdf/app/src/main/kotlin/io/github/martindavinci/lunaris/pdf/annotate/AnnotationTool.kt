package io.github.martindavinci.lunaris.pdf.annotate

import io.github.martindavinci.lunaris.pdf.viewer.ToolSpec

enum class AnnotationTool { HIGHLIGHT, PEN, TEXT, NOTE, SIGN, ERASER }

/** What each tool was last set to. Colours as the page takes them. */
data class AnnotationOptions(
    val highlightColor: String = AnnotationPalette.HIGHLIGHT_COLORS.first(),
    val highlightFree: Boolean = false,
    val penColor: String = AnnotationPalette.PEN_COLORS[1],
    val penThickness: Int = AnnotationPalette.PEN_THICKNESSES[1],
    val textColor: String = AnnotationPalette.TEXT_COLORS.first(),
    val textSize: Int = AnnotationPalette.DEFAULT_TEXT_SIZE,
)

object AnnotationPalette {
    val HIGHLIGHT_COLORS = listOf("#FFD54F", "#A8D8B9", "#90CAF9", "#F48FB1")
    val PEN_COLORS = listOf("#1C1B1F", "#E53935", "#1E66E0", "#2E7D32")
    val TEXT_COLORS = listOf("#1C1B1F", "#1E66E0", "#E53935")
    val PEN_THICKNESSES = listOf(1, 2, 4)
    const val FREE_HIGHLIGHT_THICKNESS = 16
    const val DEFAULT_TEXT_SIZE = 12
    const val MIN_TEXT_SIZE = 8
    const val MAX_TEXT_SIZE = 32
    private const val TEXT_SIZE_STEP = 2

    fun spec(tool: AnnotationTool, options: AnnotationOptions): ToolSpec = when (tool) {
        AnnotationTool.HIGHLIGHT ->
            if (options.highlightFree) {
                ToolSpec("highlightFree", options.highlightColor, FREE_HIGHLIGHT_THICKNESS)
            } else {
                ToolSpec("highlight", options.highlightColor, 0)
            }
        AnnotationTool.PEN -> ToolSpec("pen", options.penColor, options.penThickness)
        AnnotationTool.TEXT -> ToolSpec("text", options.textColor, options.textSize)
        AnnotationTool.NOTE -> ToolSpec("note", "", 0)
        AnnotationTool.SIGN -> ToolSpec("sign", "", 0)
        AnnotationTool.ERASER -> ToolSpec("eraser", "", 0)
    }

    /** [steps] up or down from [size]. */
    fun textSize(size: Int, steps: Int): Int = (size + steps * TEXT_SIZE_STEP).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
}
