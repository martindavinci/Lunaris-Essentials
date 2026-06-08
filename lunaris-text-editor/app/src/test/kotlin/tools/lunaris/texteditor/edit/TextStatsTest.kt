package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextStatsTest {
    @Test fun counts_wordsAndChars() {
        val s = TextStats.of("hello world\nfoo")
        assertThat(s.words).isEqualTo(3)
        assertThat(s.characters).isEqualTo(15)
    }
    @Test fun blank_isZeroWords() {
        val s = TextStats.of("   \n  ")
        assertThat(s.words).isEqualTo(0)
        assertThat(s.characters).isEqualTo(6)
    }
    @Test fun emptyString() {
        assertThat(TextStats.of("").words).isEqualTo(0)
        assertThat(TextStats.of("").characters).isEqualTo(0)
    }
}
