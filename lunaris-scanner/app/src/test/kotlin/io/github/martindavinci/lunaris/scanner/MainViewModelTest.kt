package io.github.martindavinci.lunaris.scanner

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.AppSettings
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currentResult = CurrentResult()
    private val recorded = mutableListOf<ScannedCode>()
    private val viewModels = mutableListOf<MainViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel(decoded: ScannedCode?) = MainViewModel(
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        ),
        currentResult = currentResult,
        imageDecoder = { decoded },
        scanRecorder = { recorded += it },
    ).also { viewModels += it }

    private suspend fun MainViewModel.awaitSharedImage(expected: SharedImageState) =
        state.first { it.settings != null && it.sharedImage == expected }

    @Test fun normalLaunch_exposesStoredSettingsAndNoSharedImage() = runBlocking {
        val state = viewModel(decoded = null).state.first { it.settings != null }

        assertThat(state).isEqualTo(MainUiState(AppSettings(), SharedImageState.NONE))
    }

    @Test fun sharedImageWithCode_opensResultAndRecordsIt() = runBlocking<Unit> {
        val code = ScannedCode("hello", CodeFormat.QR_CODE)
        val viewModel = viewModel(decoded = code)

        viewModel.onAction(MainAction.ImageShared("content://image"))
        viewModel.awaitSharedImage(SharedImageState.FOUND)

        assertThat(currentResult.code.value).isEqualTo(code)
        assertThat(recorded).containsExactly(code)
    }

    @Test fun sharedImageWithoutCode_recordsNothing() = runBlocking {
        val viewModel = viewModel(decoded = null)

        viewModel.onAction(MainAction.ImageShared("content://image"))
        viewModel.awaitSharedImage(SharedImageState.NOT_FOUND)

        assertThat(currentResult.code.value).isNull()
        assertThat(recorded).isEmpty()
    }
}
