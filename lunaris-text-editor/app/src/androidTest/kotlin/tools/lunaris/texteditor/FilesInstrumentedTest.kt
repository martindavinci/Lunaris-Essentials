package tools.lunaris.texteditor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilesInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
}
