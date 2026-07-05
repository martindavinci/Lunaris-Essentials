package tools.lunaris.texteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: launching [MainActivity] exercises the full app init — Application.onCreate,
 * the Hilt graph, and TextMate grammar/theme registration from the REAL bundled assets.
 * If startup throws (as it did for the single-string-ext JSON bug and the doubled-scope
 * grammar bug), this fails before any assertion. This is the runtime check that plain-JVM
 * unit tests structurally cannot provide.
 */
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_withoutCrashing() {
        // Reaching the composed editor top bar means Application + Hilt + MainActivity all
        // initialized without throwing.
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }
}
