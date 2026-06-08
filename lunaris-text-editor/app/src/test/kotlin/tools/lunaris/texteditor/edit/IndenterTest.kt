package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndenterTest {
    @Test fun increase_addsUnitToEachSelectedLine() {
        assertThat(Indenter.increase("a\nb\nc", selStart = 0, selEnd = 3, unit = "  ")).isEqualTo("  a\n  b\nc")
    }
    @Test fun decrease_removesLeadingUnit() {
        assertThat(Indenter.decrease("    a\n  b\nc", selStart = 0, selEnd = 6, unit = "  ")).isEqualTo("  a\nb\nc")
    }
    @Test fun decrease_noLeadingWhitespace_noChange() {
        assertThat(Indenter.decrease("a", 0, 1, "  ")).isEqualTo("a")
    }
    @Test fun increase_cursorOnly_indentsCurrentLine() {
        assertThat(Indenter.increase("a\nb\nc", selStart = 2, selEnd = 2, unit = "  ")).isEqualTo("a\n  b\nc")
    }
}
