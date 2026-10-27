package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchHintsTest {

    private val history = listOf("Novel", "Memory", "novella")

    @Test fun emptyQuery_showsAllRecents() {
        assertThat(searchHints(history, "")).containsExactlyElementsIn(history).inOrder()
    }

    @Test fun filtersByContains_ignoringCase() {
        assertThat(searchHints(history, "NOV")).containsExactly("Novel", "novella").inOrder()
    }

    @Test fun hidesTheExactQuery() {
        assertThat(searchHints(history, "Novel")).containsExactly("novella")
    }
}
