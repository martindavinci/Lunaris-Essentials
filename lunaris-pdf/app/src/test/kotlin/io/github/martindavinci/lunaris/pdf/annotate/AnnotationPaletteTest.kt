package io.github.martindavinci.lunaris.pdf.annotate

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.viewer.ToolSpec
import org.junit.Test

class AnnotationPaletteTest {
    private val options = AnnotationOptions()

    @Test fun eachTool_namesItselfToThePage() {
        assertThat(AnnotationPalette.spec(AnnotationTool.HIGHLIGHT, options))
            .isEqualTo(ToolSpec("highlight", options.highlightColor, 0))
        assertThat(AnnotationPalette.spec(AnnotationTool.PEN, options))
            .isEqualTo(ToolSpec("pen", options.penColor, options.penThickness))
        assertThat(AnnotationPalette.spec(AnnotationTool.TEXT, options))
            .isEqualTo(ToolSpec("text", options.textColor, options.textSize))
        assertThat(AnnotationPalette.spec(AnnotationTool.NOTE, options).tool).isEqualTo("note")
        assertThat(AnnotationPalette.spec(AnnotationTool.SIGN, options).tool).isEqualTo("sign")
        assertThat(AnnotationPalette.spec(AnnotationTool.ERASER, options).tool).isEqualTo("eraser")
    }

    @Test fun freeHighlight_isItsOwnTool_withAWideLine() {
        val spec = AnnotationPalette.spec(AnnotationTool.HIGHLIGHT, options.copy(highlightFree = true))
        assertThat(spec.tool).isEqualTo("highlightFree")
        assertThat(spec.size).isEqualTo(AnnotationPalette.FREE_HIGHLIGHT_THICKNESS)
    }

    @Test fun textSize_staysInRange() {
        assertThat(AnnotationPalette.textSize(AnnotationPalette.MAX_TEXT_SIZE, 1)).isEqualTo(AnnotationPalette.MAX_TEXT_SIZE)
        assertThat(AnnotationPalette.textSize(AnnotationPalette.MIN_TEXT_SIZE, -1)).isEqualTo(AnnotationPalette.MIN_TEXT_SIZE)
        assertThat(AnnotationPalette.textSize(12, 1)).isEqualTo(14)
    }

    @Test fun defaults_comeFromThePalettes() {
        assertThat(AnnotationPalette.HIGHLIGHT_COLORS).contains(options.highlightColor)
        assertThat(AnnotationPalette.PEN_COLORS).contains(options.penColor)
        assertThat(AnnotationPalette.TEXT_COLORS).contains(options.textColor)
        assertThat(AnnotationPalette.PEN_THICKNESSES).contains(options.penThickness)
    }
}
