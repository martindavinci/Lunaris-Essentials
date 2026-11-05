package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.document.DocumentInbox
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PageSize
import io.github.martindavinci.lunaris.scanner.scan.CodeDecoder
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
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A photo that does nothing, for the tests that are not about documents. */
internal class NoDocumentPhotos : DocumentPhotos {
    override suspend fun imported(uri: String): ImportedPhoto? = null

    override suspend fun captured(path: String): ImportedPhoto? = null

    override suspend fun load(path: String, longSide: Int): PagePixels? = null

    override fun delete(path: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelDocumentTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inbox = DocumentInbox()
    private val viewModels = mutableListOf<ScanViewModel>()
    private val photo = ImportedPhoto("work", PageSize(4, 3))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel(photos: DocumentPhotos) = ScanViewModel(
        decoder = CodeDecoder(),
        imageDecoder = { null },
        scanRecorder = {},
        currentResult = CurrentResult(),
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        ),
        documentPhotos = photos,
        documentInbox = inbox,
    ).also { viewModels += it }

    private val deleted = mutableListOf<String>()

    private fun reading(result: ImportedPhoto?) = object : DocumentPhotos by NoDocumentPhotos() {
        override fun delete(path: String) {
            deleted += path
        }

        override suspend fun imported(uri: String) = result

        override suspend fun captured(path: String) = result
    }

    @Test fun startsOnCodes() {
        assertThat(viewModel(NoDocumentPhotos()).state.value.mode).isEqualTo(ScanMode.CODE)
    }

    @Test fun theModeCanChange() {
        val viewModel = viewModel(NoDocumentPhotos())

        viewModel.onAction(ScanAction.ModeSelected(ScanMode.DOCUMENT))

        assertThat(viewModel.state.value.mode).isEqualTo(ScanMode.DOCUMENT)
    }

    @Test fun aPickedImage_landsInTheInbox_andOpensTheDocument() = runBlocking<Unit> {
        val viewModel = viewModel(reading(photo))

        viewModel.onAction(ScanAction.DocumentImagePicked("content://picture"))

        withTimeout(1000) { viewModel.documents.first() }
        assertThat(inbox.take()).isEqualTo(photo)
        assertThat(viewModel.state.value.decodingImage).isFalse()
    }

    @Test fun aTakenPhoto_landsInTheInbox_andOpensTheDocument() = runBlocking<Unit> {
        val viewModel = viewModel(reading(photo))
        viewModel.onAction(ScanAction.ShutterPressed)

        viewModel.onAction(ScanAction.DocumentPhotoTaken("shot"))

        withTimeout(1000) { viewModel.documents.first() }
        assertThat(inbox.take()).isEqualTo(photo)
    }

    @Test fun anUnreadableImage_saysSo_andOpensNothing() {
        val viewModel = viewModel(reading(null))

        viewModel.onAction(ScanAction.DocumentImagePicked("content://picture"))

        assertThat(viewModel.state.value.documentPhotoFailed).isTrue()
        assertThat(viewModel.state.value.decodingImage).isFalse()
        assertThat(inbox.take()).isNull()

        viewModel.onAction(ScanAction.DocumentFailureShown)
        assertThat(viewModel.state.value.documentPhotoFailed).isFalse()
    }

    @Test fun aPhotoTheCameraCouldNotTake_saysSo() {
        val viewModel = viewModel(NoDocumentPhotos())
        viewModel.onAction(ScanAction.ShutterPressed)

        viewModel.onAction(ScanAction.DocumentPhotoFailed)

        assertThat(viewModel.state.value.documentPhotoFailed).isTrue()
    }

    @Test fun aPhotoArrivingAfterChangingMode_isDropped() {
        val viewModel = viewModel(reading(photo))
        viewModel.onAction(ScanAction.ShutterPressed)
        viewModel.onAction(ScanAction.ModeSelected(ScanMode.CODE))

        viewModel.onAction(ScanAction.DocumentPhotoTaken("shot"))

        assertThat(inbox.take()).isNull()
        assertThat(deleted).containsExactly("shot")
    }

    @Test fun aPhotoArrivingAfterLeaving_isDropped() {
        val viewModel = viewModel(reading(photo))
        viewModel.onAction(ScanAction.ShutterPressed)
        viewModel.onAction(ScanAction.ScreenLeft)

        viewModel.onAction(ScanAction.DocumentPhotoTaken("shot"))

        assertThat(inbox.take()).isNull()
        assertThat(deleted).containsExactly("shot")
    }

    @Test fun aCameraFailureAfterLeaving_saysNothing() {
        val viewModel = viewModel(NoDocumentPhotos())
        viewModel.onAction(ScanAction.ShutterPressed)
        viewModel.onAction(ScanAction.ScreenLeft)

        viewModel.onAction(ScanAction.DocumentPhotoFailed)

        assertThat(viewModel.state.value.documentPhotoFailed).isFalse()
    }

    @Test fun leaving_forgetsAFailureNotShownYet() {
        val viewModel = viewModel(reading(null))
        viewModel.onAction(ScanAction.DocumentImagePicked("content://picture"))

        viewModel.onAction(ScanAction.ScreenLeft)

        assertThat(viewModel.state.value.documentPhotoFailed).isFalse()
    }

    @Test fun leavingWhileAPhotoIsRead_opensNothing() {
        val gate = CompletableDeferred<ImportedPhoto?>()
        val slow = object : DocumentPhotos by NoDocumentPhotos() {
            override suspend fun imported(uri: String) = gate.await()
        }
        val viewModel = viewModel(slow)
        viewModel.onAction(ScanAction.DocumentImagePicked("content://picture"))
        assertThat(viewModel.state.value.decodingImage).isTrue()

        viewModel.onAction(ScanAction.ScreenLeft)
        gate.complete(photo)

        assertThat(viewModel.state.value.decodingImage).isFalse()
        assertThat(inbox.take()).isNull()
    }
}
