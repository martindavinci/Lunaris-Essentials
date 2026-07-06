package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchEngineTest {
    private val engine = SearchEngine()
    private val opts = SearchOptions(caseSensitive = false, regex = false, wrap = true)

    @Test fun findNext_findsFirstFromOffset() {
        assertThat(engine.findNext("abc abc", "abc", 1, opts)).isEqualTo(MatchRange(4, 7))
    }
    @Test fun findNext_wrapsAround() {
        assertThat(engine.findNext("abc", "abc", 1, opts)).isEqualTo(MatchRange(0, 3))
    }
    @Test fun findNext_noWrap_returnsNullPastLast() {
        assertThat(engine.findNext("abc", "abc", 1, opts.copy(wrap = false))).isNull()
    }
    @Test fun caseSensitive_respected() {
        assertThat(engine.findNext("ABC", "abc", 0, opts.copy(caseSensitive = true))).isNull()
        assertThat(engine.findNext("ABC", "abc", 0, opts.copy(caseSensitive = false))).isEqualTo(MatchRange(0, 3))
    }
    @Test fun regex_matches() {
        assertThat(engine.findNext("a1b2", "[0-9]", 0, opts.copy(regex = true))).isEqualTo(MatchRange(1, 2))
    }
    @Test fun findPrevious_findsBeforeOffset() {
        assertThat(engine.findPrevious("abc abc", "abc", 7, opts)).isEqualTo(MatchRange(4, 7))
    }
    @Test fun findAll_returnsEveryMatchInOrder() {
        assertThat(engine.findAll("abc abc abc", "abc", opts))
            .containsExactly(MatchRange(0, 3), MatchRange(4, 7), MatchRange(8, 11)).inOrder()
    }
    @Test fun findAll_emptyQuery_isEmpty() {
        assertThat(engine.findAll("abc", "", opts)).isEmpty()
    }
    @Test fun replaceAll_replacesEveryMatch_returnsCount() {
        val r = engine.replaceAll("a a a", "a", "b", opts)
        assertThat(r.text).isEqualTo("b b b")
        assertThat(r.count).isEqualTo(3)
    }
    @Test fun replaceAll_regexWithGroups() {
        val r = engine.replaceAll("x=1;y=2", "(\\w)=(\\d)", "\$2=\$1", opts.copy(regex = true))
        assertThat(r.text).isEqualTo("1=x;2=y")
        assertThat(r.count).isEqualTo(2)
    }
    @Test fun emptyQuery_findsNothing() {
        assertThat(engine.findNext("abc", "", 0, opts)).isNull()
        assertThat(engine.replaceAll("abc", "", "x", opts).count).isEqualTo(0)
    }
    @Test fun invalidRegex_findsNothing_doesNotThrow() {
        assertThat(engine.findNext("abc", "(", 0, opts.copy(regex = true))).isNull()
    }
}
