package io.github.martindavinci.lunaris.pdf.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageSentencesTest {
    @Test fun sentences_areSplitAtTheirEnd() {
        assertThat(PageSentences.of("One here. Two here! Three here? Four"))
            .containsExactly("One here.", "Two here!", "Three here?", "Four").inOrder()
    }

    @Test fun lineBreaksInsideASentence_becomeSpaces() {
        assertThat(PageSentences.of("A line that\nkeeps going.")).containsExactly("A line that keeps going.")
    }

    @Test fun wordCutAtTheEndOfALine_isJoined() {
        assertThat(PageSentences.of("A hyphen-\nated word.")).containsExactly("A hyphenated word.")
    }

    @Test fun blankText_hasNoSentences() {
        assertThat(PageSentences.of(" \n\t ")).isEmpty()
        assertThat(PageSentences.of("")).isEmpty()
    }

    @Test fun cjkSentences_areSplitToo() {
        assertThat(PageSentences.of("月は明るい。星も見える。")).containsExactly("月は明るい。", "星も見える。").inOrder()
    }
}
