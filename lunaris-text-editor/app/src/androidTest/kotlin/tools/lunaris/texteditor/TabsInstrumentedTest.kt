package tools.lunaris.texteditor

import androidx.compose.ui.test.assertCountEquals
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
class TabsInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawerNew_showsTabStrip_thenCloseHidesIt() {
        // Single open file: the tab strip (and its close buttons) is hidden.
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(0)
        // Create a second tab from the File menu; now the strip appears with two tabs.
        composeRule.onNodeWithContentDescription("File").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(2)
        // Close one tab → back to a single file → strip hidden again.
        composeRule.onAllNodesWithContentDescription("Close tab")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(0)
    }
}
