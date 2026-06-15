package tools.lunaris.texteditor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overflow_run_opensPreview_thenBack() {
        composeRule.onNodeWithContentDescription("More").performClick()
        // "Run" sits in the curated overflow menu (short, fully visible).
        composeRule.onNodeWithText("Run").performClick()
        // Preview screen reached: its Print action is unique to it. waitUntil lets the nav
        // transition settle; if Print never appears the test fails here.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Print").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        // Back on the editor: the Open toolbar action is unique to it.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Open").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
