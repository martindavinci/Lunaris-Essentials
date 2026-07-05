package tools.lunaris.texteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawer_opensSettingsScreen() {
        // Settings is the gear icon in the drawer's nav rail (it's intentionally excluded from the action sheet).
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        // "General" is the first (top, on-screen) settings section — confirms the screen opened.
        composeRule.onNodeWithText("General").assertIsDisplayed()
    }
}
