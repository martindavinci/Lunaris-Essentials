package tools.lunaris.texteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun editorScaffold_showsTitleAndToolbarActions() {
        composeRule.onNodeWithText("untitled").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New").assertIsDisplayed()
    }
}
