package tools.lunaris.texteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun editorScaffold_showsTitleAndDefaultToolbar() {
        // With a single open file the tab strip is hidden, so "untitled" shows once (title bar).
        composeRule.onAllNodesWithText("untitled").onFirst().assertIsDisplayed()
        // Simplified default bottom bar: grouped cursor arrows + accented preview.
        composeRule.onNodeWithContentDescription("Cursor left").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cursor right").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Preview").assertIsDisplayed()
    }

    @Test
    fun subtitle_showsPlainTextForFreshDoc() {
        // A fresh untitled doc has no grammar (Auto/null scope) → localized "Plain text".
        composeRule.onNodeWithText("Plain text").assertIsDisplayed()
    }

    @Test
    fun topBar_hasSearchSaveAndMore_andSheetOpens() {
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More").performClick()
        // Sheet shows a group header + a known action label.
        composeRule.onNodeWithText("FILE").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed()
    }

    @Test
    fun saveIcon_disabledWhenClean() {
        // A fresh untitled doc is clean, so the top-bar Save is disabled.
        composeRule.onNodeWithContentDescription("Save").assertIsNotEnabled()
    }
}
