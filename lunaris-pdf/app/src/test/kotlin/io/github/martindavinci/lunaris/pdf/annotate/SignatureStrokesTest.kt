package io.github.martindavinci.lunaris.pdf.annotate

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignatureStrokesTest {
    @Test fun noStrokes_haveNoBounds() {
        assertThat(SignatureStrokes.bounds(emptyList(), margin = 4f)).isNull()
        assertThat(SignatureStrokes.bounds(listOf(emptyList()), margin = 4f)).isNull()
    }

    @Test fun bounds_holdEveryPoint_withTheMargin() {
        val strokes = listOf(listOf(StrokePoint(10f, 20f), StrokePoint(30f, 5f)), listOf(StrokePoint(50f, 40f)))
        assertThat(SignatureStrokes.bounds(strokes, margin = 4f)).isEqualTo(StrokeBounds(6f, 1f, 54f, 44f))
    }
}
