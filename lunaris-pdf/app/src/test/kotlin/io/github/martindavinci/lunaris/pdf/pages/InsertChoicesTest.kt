package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InsertChoicesTest {
    @Test
    fun `a short document offers every place`() {
        assertThat(InsertChoices.around(2, 3)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `a long one offers the start, the end and what is near`() {
        val choices = InsertChoices.around(500, 1000)
        assertThat(choices.first()).isEqualTo(0)
        assertThat(choices.last()).isEqualTo(1000)
        assertThat(choices).contains(500)
        assertThat(choices).contains(500 + InsertChoices.NEAR)
        assertThat(choices).doesNotContain(500 + InsertChoices.NEAR + 1)
        assertThat(choices).isInOrder()
    }

    @Test
    fun `near the ends nothing repeats`() {
        val choices = InsertChoices.around(1, 1000)
        assertThat(choices.toSet()).hasSize(choices.size)
    }
}
