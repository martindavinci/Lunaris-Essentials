package tools.lunaris.texteditor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun newTab_thenClose_changesTabCount() {
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Close tab")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Close tab").assertCountEquals(1)
    }
}
