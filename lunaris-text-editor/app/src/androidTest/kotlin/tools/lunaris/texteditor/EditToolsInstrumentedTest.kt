package tools.lunaris.texteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditToolsInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sheet_opensStatsDialog() {
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Statistics").performScrollTo().performClick()
        composeRule.onNodeWithText("Words: 0").assertIsDisplayed()
    }

    @Test
    fun sheet_opensSearchDialog() {
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Search").performScrollTo().performClick()
        composeRule.onNodeWithText("Replace all").assertIsDisplayed()
    }
}
