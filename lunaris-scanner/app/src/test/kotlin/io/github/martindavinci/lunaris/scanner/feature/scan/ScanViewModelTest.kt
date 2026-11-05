package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currentResult = CurrentResult()
    private val recorded = mutableListOf<ScannedCode>()
    private val viewModels = mutableListOf<ScanViewModel>()
    private val code = ScannedCode("hello", CodeFormat.QR_CODE)
    private val settingsStore by lazy {
        SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") })
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel(imageDecoder: ImageDecoder = ImageDecoder { null }) = ScanViewModel(
        decoder = CodeDecoder(),
        imageDecoder = imageDecoder,
        scanRecorder = { recorded += it },
        currentResult = currentResult,
        settingsStore = settingsStore,
        clock = { 0L },
    ).also { viewModels += it }

    private fun ScanViewModel.check(granted: Boolean, showRationale: Boolean) =
        onAction(ScanAction.PermissionChecked(granted, showRationale))

    @Test fun startsCheckingWithNoMessages() {
        assertThat(viewModel().state.value).isEqualTo(ScanUiState())
        assertThat(ScanUiState().cameraAccess).isEqualTo(CameraAccess.CHECKING)
    }

    @Test fun grantedPermission_opensTheCamera() {
        val viewModel = viewModel()

        viewModel.check(granted = true, showRationale = false)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.GRANTED)
    }

    @Test fun neverAsked_requestsThePermission() {
        val viewModel = viewModel()

        viewModel.check(granted = false, showRationale = false)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.NEEDS_REQUEST)
    }

    @Test fun requesting_doesNotAskAgainWhileTheDialogIsOpen() {
        val viewModel = viewModel()
        viewModel.check(granted = false, showRationale = false)

        viewModel.onAction(ScanAction.PermissionRequested)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.CHECKING)
    }

    @Test fun deniedOnce_showsTheRationale() {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.PermissionRequested)

        viewModel.check(granted = false, showRationale = true)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.RATIONALE)
    }

    @Test fun deniedWithoutRationaleAfterAsking_isPermanent() {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.PermissionRequested)

        viewModel.check(granted = false, showRationale = false)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.PERMANENTLY_DENIED)
    }

    @Test fun grantedInSystemSettings_opensTheCameraOnReturn() {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.PermissionRequested)
        viewModel.check(granted = false, showRationale = false)

        viewModel.check(granted = true, showRationale = false)

        assertThat(viewModel.state.value.cameraAccess).isEqualTo(CameraAccess.GRANTED)
    }

    @Test fun cameraHit_recordsSetsTheResultAndReports() = runBlocking {
        val viewModel = viewModel()

        viewModel.onAction(ScanAction.CodeDetected(code))

        assertThat(viewModel.hits.first()).isEqualTo(ScanHit(vibrate = true))
        assertThat(recorded).containsExactly(code)
        assertThat(currentResult.code.value).isEqualTo(code)
    }

    @Test fun vibrationSwitchedOff_isReported() = runBlocking {
        settingsStore.setVibrateOnScan(false)
        val viewModel = viewModel()

        viewModel.onAction(ScanAction.CodeDetected(code))

        assertThat(viewModel.hits.first()).isEqualTo(ScanHit(vibrate = false))
    }

    @Test fun pickedImageWithCode_isAHit() = runBlocking {
        val viewModel = viewModel(imageDecoder = { code })

        viewModel.onAction(ScanAction.ImagePicked("content://image"))

        assertThat(viewModel.hits.first()).isEqualTo(ScanHit(vibrate = true))
        assertThat(recorded).containsExactly(code)
        assertThat(currentResult.code.value).isEqualTo(code)
        assertThat(viewModel.state.first { !it.decodingImage }.noCodeFound).isFalse()
    }

    @Test fun pickedImageWithoutCode_showsTheMessageOnly() = runBlocking {
        val viewModel = viewModel(imageDecoder = { null })

        viewModel.onAction(ScanAction.ImagePicked("content://image"))

        val state = viewModel.state.first { !it.decodingImage }
        assertThat(state.noCodeFound).isTrue()
        assertThat(recorded).isEmpty()
        assertThat(currentResult.code.value).isNull()
    }

    @Test fun whileAnImageIsRead_stateSaysSo() = runBlocking {
        val gate = CompletableDeferred<ScannedCode?>()
        val viewModel = viewModel(imageDecoder = { gate.await() })

        viewModel.onAction(ScanAction.ImagePicked("content://image"))
        assertThat(viewModel.state.value.decodingImage).isTrue()

        gate.complete(null)
        assertThat(viewModel.state.first { !it.decodingImage }.noCodeFound).isTrue()
    }

    @Test fun messageShown_clearsIt() = runBlocking {
        val viewModel = viewModel(imageDecoder = { null })
        viewModel.onAction(ScanAction.ImagePicked("content://image"))
        viewModel.state.first { it.noCodeFound }

        viewModel.onAction(ScanAction.NoCodeMessageShown)

        assertThat(viewModel.state.value.noCodeFound).isFalse()
    }

    @Test fun pickingAgain_clearsTheOldMessage() = runBlocking<Unit> {
        val gate = CompletableDeferred<ScannedCode?>()
        var first = true
        val viewModel = viewModel(imageDecoder = {
            if (first) {
                first = false
                null
            } else {
                gate.await()
            }
        })
        viewModel.onAction(ScanAction.ImagePicked("content://first"))
        viewModel.state.first { it.noCodeFound }

        viewModel.onAction(ScanAction.ImagePicked("content://second"))

        assertThat(viewModel.state.value.noCodeFound).isFalse()
        gate.complete(null)
    }

    @Test fun cameraFailure_isRemembered() {
        val viewModel = viewModel()

        viewModel.onAction(ScanAction.CameraUnavailable)

        assertThat(viewModel.state.value.cameraUnavailable).isTrue()
    }
}
