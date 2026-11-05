package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.scan.CodeDecoder
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.stopCoroutinesAndResetMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelChoiceTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModels = mutableListOf<ScanViewModel>()
    private var nowMillis = 0L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel() = ScanViewModel(
        decoder = CodeDecoder(),
        imageDecoder = { null },
        scanRecorder = {},
        currentResult = CurrentResult(),
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        ),
        clock = { nowMillis },
    ).also { viewModels += it }

    private fun ScanViewModel.onCodes() = also { it.onAction(ScanAction.CodeChosen) }

    private val ScanViewModel.choosing get() = state.value.choosing

    private fun minutes(count: Long) = count * 60_000

    @Test fun itStartsOnTheChoice() {
        assertThat(viewModel().choosing).isTrue()
    }

    @Test fun choosingCodes_opensTheCamera() {
        assertThat(viewModel().onCodes().choosing).isFalse()
    }

    @Test fun backFromTheCamera_returnsToTheChoice() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.ChoiceAsked)

        assertThat(viewModel.choosing).isTrue()
    }

    @Test fun aRequestForTheChoice_counts() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.Requested(ScanRequest(id = 1, codes = false)))

        assertThat(viewModel.choosing).isTrue()
    }

    @Test fun aRequestForCodes_opensTheCamera() {
        val viewModel = viewModel()

        viewModel.onAction(ScanAction.Requested(ScanRequest(id = 1, codes = true)))

        assertThat(viewModel.choosing).isFalse()
    }

    @Test fun aRequestSeenAgainAfterReturningToTheScreen_doesNothing() {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.Requested(ScanRequest(id = 1, codes = true)))
        viewModel.onAction(ScanAction.ChoiceAsked)

        viewModel.onAction(ScanAction.Requested(ScanRequest(id = 1, codes = true)))

        assertThat(viewModel.choosing).isTrue()
    }

    @Test fun readingACodeAndComingBack_staysOnTheCamera() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.ScreenLeft)

        assertThat(viewModel.choosing).isFalse()
    }

    @Test fun comingBackWithinFiveMinutes_staysOnTheCamera() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.ScreenHidden)
        nowMillis += minutes(4)
        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.choosing).isFalse()
    }

    @Test fun comingBackAfterFiveMinutes_returnsToTheChoice() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.ScreenHidden)
        nowMillis += minutes(5)
        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.choosing).isTrue()
    }

    @Test fun aLongStayOnTheResult_isNoAbsence() {
        val viewModel = viewModel().onCodes()

        viewModel.onAction(ScanAction.ScreenHidden)
        viewModel.onAction(ScanAction.ScreenLeft)
        nowMillis += minutes(10)
        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.choosing).isFalse()
    }

    @Test fun aPauseCountsOnlyOnce() {
        val viewModel = viewModel().onCodes()
        viewModel.onAction(ScanAction.ScreenHidden)
        nowMillis += minutes(1)
        viewModel.onAction(ScanAction.ScreenShown)
        nowMillis += minutes(10)

        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.choosing).isFalse()
    }
}
