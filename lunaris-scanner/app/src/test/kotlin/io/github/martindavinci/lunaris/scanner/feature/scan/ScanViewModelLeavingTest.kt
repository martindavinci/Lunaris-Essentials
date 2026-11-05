package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.document.DocumentInbox
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.scan.CodeDecoder
import io.github.martindavinci.lunaris.scanner.scan.ImageDecoder
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.stopCoroutinesAndResetMain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Nothing may reach the Result screen after the user has left the Scan screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelLeavingTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currentResult = CurrentResult()
    private val recorded = mutableListOf<ScannedCode>()
    private val viewModels = mutableListOf<ScanViewModel>()
    private val code = ScannedCode("hello", CodeFormat.QR_CODE)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel(imageDecoder: ImageDecoder = ImageDecoder { null }) = ScanViewModel(
        decoder = CodeDecoder(),
        imageDecoder = imageDecoder,
        scanRecorder = { recorded += it },
        currentResult = currentResult,
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        ),
        documentPhotos = NoDocumentPhotos(),
        documentInbox = DocumentInbox(),
    ).also { viewModels += it }

    @Test fun leavingWhileAnImageIsRead_dropsIt() = runBlocking<Unit> {
        val gate = CompletableDeferred<ScannedCode?>()
        val viewModel = viewModel(imageDecoder = { gate.await() })
        viewModel.onAction(ScanAction.ImagePicked("content://image"))

        viewModel.onAction(ScanAction.ScreenLeft)
        gate.complete(code)
        delay(SETTLE_MILLIS)

        assertThat(viewModel.state.value.decodingImage).isFalse()
        assertThat(viewModel.state.value.noCodeFound).isFalse()
        assertThat(recorded).isEmpty()
        assertThat(currentResult.code.value).isNull()
        assertThat(withTimeoutOrNull(SETTLE_MILLIS) { viewModel.hits.first() }).isNull()
    }

    @Test fun leaving_discardsAHitNobodyReceived() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.CodeDetected(code))
        currentResult.code.first { it != null }
        delay(SETTLE_MILLIS)

        viewModel.onAction(ScanAction.ScreenLeft)

        assertThat(withTimeoutOrNull(SETTLE_MILLIS) { viewModel.hits.first() }).isNull()
    }

    @Test fun afterLeaving_theNextCodeStillWorks() = runBlocking {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.ScreenLeft)

        viewModel.onAction(ScanAction.CodeDetected(code))

        assertThat(viewModel.hits.first()).isEqualTo(ScanHit(vibrate = true))
        assertThat(currentResult.code.value).isEqualTo(code)
    }

    private companion object {
        const val SETTLE_MILLIS = 100L
    }
}
