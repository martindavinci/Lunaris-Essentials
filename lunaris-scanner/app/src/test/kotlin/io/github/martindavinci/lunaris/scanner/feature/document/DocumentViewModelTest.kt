package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.DocumentFiles
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PagePoint
import io.github.martindavinci.lunaris.scanner.document.PageReading
import io.github.martindavinci.lunaris.scanner.document.PageRendering
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import io.github.martindavinci.lunaris.scanner.document.PageSize
import io.github.martindavinci.lunaris.scanner.document.PaperSize
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModelTest {
    private class FakePhotos : DocumentPhotos {
        val deleted = mutableListOf<String>()
        var importsWork = true
        var loadsWork = true

        override suspend fun imported(uri: String) = if (importsWork) ImportedPhoto(if (uri == "first") uri else "from-$uri", PageSize(40, 20)) else null

        override suspend fun captured(path: String) = if (importsWork) ImportedPhoto("work-$path", PageSize(40, 20)) else null

        var loadGate: CompletableDeferred<Unit>? = null

        override suspend fun load(path: String, longSide: Int): PagePixels? {
            loadGate?.await()
            return if (loadsWork) PagePixels(IntArray(2), 2, 1) else null
        }

        override fun delete(path: String) {
            deleted += path
        }
    }

    private class FakeFiles : DocumentFiles {
        var works = true
        var gate: CompletableDeferred<Unit>? = null
        val pdfs = mutableListOf<Pair<String, List<DocumentPage>>>()
        val pictures = mutableListOf<Triple<String, String, PictureFormat>>()
        val discarded = mutableListOf<String>()

        override suspend fun savePdf(uri: String, pages: List<DocumentPage>): Boolean {
            gate?.await()
            pdfs += uri to pages
            return works
        }

        override suspend fun savePicture(uri: String, page: DocumentPage, format: PictureFormat): Boolean {
            pictures += Triple(uri, page.photoPath, format)
            return works
        }

        val folders = mutableListOf<Triple<String, List<DocumentPage>, PictureFormat>>()

        override suspend fun savePictures(folderUri: String, pages: List<DocumentPage>, format: PictureFormat): Boolean {
            folders += Triple(folderUri, pages, format)
            return works
        }

        override suspend fun discard(uri: String) {
            discarded += uri
        }

        override suspend fun cachePdfForSharing(pages: List<DocumentPage>) = if (works) "content://shared" else null
    }

    private val photos = FakePhotos()
    private val files = FakeFiles()
    private var nowMillis = 0L
    private val rendered = mutableListOf<PageSettings>()
    private var renderWorks = true
    private val rendering = PageRendering { _, settings, _, _ ->
        rendered += settings
        if (renderWorks) PagePixels(IntArray(1), 1, 1) else null
    }
    private val tilted = PageCorners(PagePoint(0.1f, 0.2f), PagePoint(0.9f, 0.1f), PagePoint(0.8f, 0.9f), PagePoint(0.2f, 0.8f))

    private class FakeReading : PageReading {
        var found: PageCorners? = null
        var suggestion = CleanMode.COLOUR
        var suggestions = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun find(photo: PagePixels) = found

        override suspend fun suggestClean(photo: PagePixels, corners: PageCorners): CleanMode {
            suggestions++
            gate?.await()
            return suggestion
        }
    }

    private val reading = FakeReading()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(first: Boolean = true): DocumentViewModel {
        val viewModel = DocumentViewModel(photos, files, rendering, reading) { nowMillis }
        if (first) viewModel.onAction(DocumentAction.PhotoPicked("first"))
        return viewModel
    }

    private fun DocumentViewModel.confirmCorners() = onAction(DocumentAction.CornersDone)

    private fun reviewing() = viewModel().also { it.confirmCorners() }

    private fun DocumentViewModel.addPage(uri: String = "second") {
        onAction(DocumentAction.PhotoPicked(uri))
        confirmCorners()
    }

    private val DocumentViewModel.now get() = state.value

    @Test fun aPageFoundOnThePhoto_setsTheCorners() {
        reading.found = tilted

        val viewModel = viewModel()

        assertThat(viewModel.now.corners).isEqualTo(tilted)
        assertThat(viewModel.now.photo).isNotNull()
    }

    @Test fun nothingFound_leavesTheStartingCorners() {
        assertThat(viewModel().now.corners).isEqualTo(PageCorners.START)
    }

    @Test fun reset_goesBackToWhatWasFound() {
        reading.found = tilted
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.5f, 0.5f)))

        viewModel.onAction(DocumentAction.CornersReset)

        assertThat(viewModel.now.corners).isEqualTo(tilted)
    }

    @Test fun reset_withNothingFound_goesBackToTheStart() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.5f, 0.5f)))

        viewModel.onAction(DocumentAction.CornersReset)

        assertThat(viewModel.now.corners).isEqualTo(PageCorners.START)
    }

    @Test fun editingTheCornersLater_startsFromThoseConfirmed_andResetFromThoseFound() {
        reading.found = tilted
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.15f, 0.2f)))
        val confirmed = viewModel.now.corners
        viewModel.confirmCorners()

        viewModel.onAction(DocumentAction.EditCorners)
        assertThat(viewModel.now.corners).isEqualTo(confirmed)

        viewModel.onAction(DocumentAction.CornersReset)
        assertThat(viewModel.now.corners).isEqualTo(tilted)
    }

    @Test fun eachNewPage_isLookedForOnItsOwnPhoto() {
        reading.found = tilted
        val viewModel = reviewing()
        val other = PageCorners(PagePoint(0.2f, 0.2f), PagePoint(0.8f, 0.2f), PagePoint(0.8f, 0.8f), PagePoint(0.2f, 0.8f))
        reading.found = other

        viewModel.onAction(DocumentAction.PhotoPicked("second"))

        assertThat(viewModel.now.corners).isEqualTo(other)
    }

    @Test fun lookingAtAnotherPage_doesNotSearchItAgain() {
        reading.found = tilted
        val viewModel = reviewing()
        viewModel.addPage()
        reading.found = null

        viewModel.onAction(DocumentAction.PageSelected(0))
        viewModel.onAction(DocumentAction.EditCorners)

        assertThat(viewModel.now.corners).isEqualTo(tilted)
    }

    @Test fun aNewPage_getsTheCleanThatSuitsIt() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE

        val viewModel = reviewing()

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
        assertThat(rendered.last().clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun whileNothingWasChosen_eachPageGetsItsOwnClean() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = reviewing()
        reading.suggestion = CleanMode.COLOUR

        viewModel.addPage()

        assertThat(viewModel.now.pages.map { it.settings.clean })
            .containsExactly(CleanMode.BLACK_AND_WHITE, CleanMode.COLOUR).inOrder()
    }

    @Test fun aCleanChosenByHand_staysAndPassesToTheNextPages() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        val before = reading.suggestions

        viewModel.addPage()

        assertThat(viewModel.now.pages.map { it.settings.clean }).containsExactly(CleanMode.GREY, CleanMode.GREY).inOrder()
        assertThat(reading.suggestions).isEqualTo(before)
    }

    @Test fun confirmingTheCornersAgain_leavesTheCleanAlone() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = reviewing()
        reading.suggestion = CleanMode.COLOUR

        viewModel.onAction(DocumentAction.EditCorners)
        viewModel.confirmCorners()

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun itStartsOnTheCamera_withNoPages() {
        val state = viewModel(first = false).now

        assertThat(state.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(state.pages).isEmpty()
        assertThat(state.closed).isFalse()
    }

    @Test fun backFromTheCameraWithNoPages_closes() {
        val viewModel = viewModel(first = false)

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isTrue()
        assertThat(viewModel.now.confirm).isNull()
    }

    @Test fun aNewPageIsMarkedSo_andAPageBeingEditedIsNot() {
        val viewModel = viewModel()
        assertThat(viewModel.now.newPage).isTrue()

        viewModel.confirmCorners()
        viewModel.onAction(DocumentAction.EditCorners)

        assertThat(viewModel.now.newPage).isFalse()
    }

    @Test fun anotherPage_keepsThisOneAndReopensTheCamera() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.2f, 0.2f)))
        val set = viewModel.now.corners

        viewModel.onAction(DocumentAction.AnotherPage)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        val page = viewModel.now.pages.single()
        assertThat(page.settings.corners).isEqualTo(set)
        assertThat(page.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
        assertThat(page.thumbnail).isNotNull()
    }

    @Test fun aPageStillBeingPrepared_isNotStoppedByTheNextOne() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val gate = CompletableDeferred<Unit>()
        reading.gate = gate
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)

        viewModel.onAction(DocumentAction.PhotoPicked("second"))
        reading.gate = null
        gate.complete(Unit)

        assertThat(viewModel.now.pages.first().settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
        assertThat(viewModel.now.pages.first().thumbnail).isNotNull()
    }

    @Test fun anotherPage_isRefusedWhileTheCornersAreCrossed() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.99f, 0.99f)))

        viewModel.onAction(DocumentAction.AnotherPage)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
    }

    @Test fun aCleanChosenWhileAPageIsPrepared_isNotOverwritten() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val gate = CompletableDeferred<Unit>()
        reading.gate = gate
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)
        viewModel.onAction(DocumentAction.FinishPages)

        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        gate.complete(Unit)

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.GREY)
    }

    @Test fun finishing_opensTheReviewOnTheLastPage() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)
        viewModel.onAction(DocumentAction.PhotoPicked("second"))
        viewModel.onAction(DocumentAction.AnotherPage)

        viewModel.onAction(DocumentAction.FinishPages)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.current).isEqualTo(1)
        assertThat(viewModel.now.result).isNotNull()
    }

    @Test fun retaking_dropsThisPhotoAndReopensTheCamera() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)
        viewModel.onAction(DocumentAction.PhotoPicked("second"))

        viewModel.onAction(DocumentAction.RetakePhoto)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("first")
        assertThat(photos.deleted).containsExactly("from-second")
    }

    @Test fun backFromTheCameraWithPages_asksBeforeDiscarding() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
        assertThat(viewModel.now.closed).isFalse()
    }

    @Test fun notFound_isSaidUntilAPointIsMoved() {
        val viewModel = viewModel()
        assertThat(viewModel.now.cornersNotFound).isTrue()

        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.1f, 0.1f)))
        assertThat(viewModel.now.cornersNotFound).isFalse()

        viewModel.onAction(DocumentAction.CornersReset)
        assertThat(viewModel.now.cornersNotFound).isTrue()
    }

    @Test fun aPageThatWasFound_saysNothingOfThatKind() {
        reading.found = tilted

        assertThat(viewModel().now.cornersNotFound).isFalse()
    }

    @Test fun cameBackAfterALongWhileWithNoPages_closes() {
        val viewModel = viewModel(first = false)

        viewModel.onAction(DocumentAction.ScreenHidden)
        nowMillis += 5 * 60_000
        viewModel.onAction(DocumentAction.ScreenShown)

        assertThat(viewModel.now.closed).isTrue()
    }

    @Test fun cameBackSoonWithNoPages_staysOnTheCamera() {
        val viewModel = viewModel(first = false)

        viewModel.onAction(DocumentAction.ScreenHidden)
        nowMillis += 4 * 60_000
        viewModel.onAction(DocumentAction.ScreenShown)

        assertThat(viewModel.now.closed).isFalse()
    }

    @Test fun cameBackAfterALongWhileWithPages_keepsThem() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.AnotherPage)

        viewModel.onAction(DocumentAction.ScreenHidden)
        nowMillis += 60 * 60_000
        viewModel.onAction(DocumentAction.ScreenShown)

        assertThat(viewModel.now.closed).isFalse()
        assertThat(viewModel.now.pages).hasSize(1)
    }

    @Test fun aPhotoArrivingAfterClosing_isDeleted() {
        val viewModel = viewModel(first = false)
        viewModel.onAction(DocumentAction.BackPressed)

        viewModel.onAction(DocumentAction.PhotoTaken("shot"))

        assertThat(photos.deleted).containsExactly("shot")
        assertThat(viewModel.now.pages).isEmpty()
    }

    @Test fun retakingWhileThePhotoIsLoading_saysNothingAboutIt() {
        val gate = CompletableDeferred<Unit>()
        photos.loadGate = gate
        photos.loadsWork = false
        val viewModel = viewModel()

        viewModel.onAction(DocumentAction.RetakePhoto)
        gate.complete(Unit)

        assertThat(viewModel.now.message).isNull()
        assertThat(viewModel.now.photo).isNull()
    }

    @Test fun deletingTheLastPageWhileItIsLoading_saysNothingAboutIt() {
        val viewModel = reviewing()
        val gate = CompletableDeferred<Unit>()
        photos.loadGate = gate
        photos.loadsWork = false
        viewModel.onAction(DocumentAction.PhotoPicked("second"))
        viewModel.onAction(DocumentAction.RetakePhoto)
        viewModel.onAction(DocumentAction.FinishPages)
        viewModel.onAction(DocumentAction.DeletePage)

        viewModel.onAction(DocumentAction.Confirmed)
        gate.complete(Unit)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.message).isNull()
    }

    @Test fun aNewScanAfterSaving_getsTheCleanThatSuitsIt_again() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))
        viewModel.onAction(DocumentAction.BackPressed)

        viewModel.onAction(DocumentAction.PhotoPicked("second"))
        viewModel.confirmCorners()

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun deletingEveryPage_alsoForgetsTheCleanChosen() {
        reading.suggestion = CleanMode.BLACK_AND_WHITE
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        viewModel.onAction(DocumentAction.DeletePage)
        viewModel.onAction(DocumentAction.Confirmed)

        viewModel.onAction(DocumentAction.PhotoPicked("second"))
        viewModel.confirmCorners()

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun whileThePermissionIsBeingAsked_itIsNotAskedAgain() {
        val viewModel = viewModel(first = false)
        viewModel.onAction(DocumentAction.CameraPermissionChecked(granted = false, showRationale = false))

        viewModel.onAction(DocumentAction.CameraPermissionRequested)

        assertThat(viewModel.now.cameraAccess).isEqualTo(CameraAccess.CHECKING)
    }

    @Test fun theCameraAsksForThePermissionOnce_thenTakesAnAnswerAsFinal() {
        val viewModel = viewModel(first = false)
        viewModel.onAction(DocumentAction.CameraPermissionChecked(granted = false, showRationale = false))
        assertThat(viewModel.now.cameraAccess).isEqualTo(CameraAccess.NEEDS_REQUEST)

        viewModel.onAction(DocumentAction.CameraPermissionRequested)
        viewModel.onAction(DocumentAction.CameraPermissionChecked(granted = false, showRationale = false))

        assertThat(viewModel.now.cameraAccess).isEqualTo(CameraAccess.PERMANENTLY_DENIED)
    }

    @Test fun aCameraThatCannotRun_isShownSo_andTheGalleryStillWorks() {
        val viewModel = viewModel(first = false)

        viewModel.onAction(DocumentAction.CameraUnavailable)
        assertThat(viewModel.now.cameraUnavailable).isTrue()

        viewModel.onAction(DocumentAction.PhotoPicked("first"))
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
    }

    @Test fun theFirstPhoto_opensItsCorners() {
        val state = viewModel().now

        assertThat(state.step).isEqualTo(DocumentStep.CORNERS)
        assertThat(state.corners).isEqualTo(PageCorners.START)
        assertThat(state.photo).isNotNull()
        assertThat(state.pages.map { it.photoPath }).containsExactly("first")
    }

    @Test fun movingACorner_isHeldUntilConfirmed() {
        val viewModel = viewModel()

        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.2f, 0.3f)))

        assertThat(viewModel.now.corners.topLeft).isEqualTo(PagePoint(0.2f, 0.3f))
        assertThat(viewModel.now.currentPage!!.settings.corners).isEqualTo(PageCorners.START)
    }

    @Test fun cornersDone_keepsThem_andRendersThePage() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.2f, 0.3f)))

        viewModel.confirmCorners()

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.currentPage!!.settings.corners.topLeft).isEqualTo(PagePoint(0.2f, 0.3f))
        assertThat(viewModel.now.result).isNotNull()
        assertThat(viewModel.now.currentPage!!.thumbnail).isNotNull()
        assertThat(viewModel.now.rendering).isFalse()
    }

    @Test fun badCorners_stayOnTheCorners() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(1f, 1f)))

        viewModel.confirmCorners()

        assertThat(viewModel.now.cornersProblem).isNotNull()
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
    }

    @Test fun resetCorners_goesBackToTheStart() {
        val viewModel = viewModel()
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.2f, 0.3f)))

        viewModel.onAction(DocumentAction.CornersReset)

        assertThat(viewModel.now.corners).isEqualTo(PageCorners.START)
    }

    @Test fun backFromANewPage_dropsItAndReopensTheCamera() {
        val viewModel = viewModel()

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages).isEmpty()
        assertThat(photos.deleted).containsExactly("first")
        assertThat(viewModel.now.closed).isFalse()
    }

    @Test fun backFromEditingCorners_forgetsTheMoves() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.EditCorners)
        viewModel.onAction(DocumentAction.CornerMoved(0, PagePoint(0.2f, 0.3f)))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.currentPage!!.settings.corners).isEqualTo(PageCorners.START)
        assertThat(viewModel.now.pages).hasSize(1)
    }

    @Test fun editCorners_startsFromThePagesOwn() {
        val viewModel = viewModel()
        tilted.points.forEachIndexed { index, point -> viewModel.onAction(DocumentAction.CornerMoved(index, point)) }
        viewModel.confirmCorners()
        viewModel.onAction(DocumentAction.CornersReset)

        viewModel.onAction(DocumentAction.EditCorners)

        assertThat(viewModel.now.corners).isEqualTo(tilted)
    }

    @Test fun choosingACleanUp_changesThatPage_andRendersAgain() {
        val viewModel = reviewing()
        rendered.clear()

        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.BLACK_AND_WHITE))

        assertThat(viewModel.now.currentPage!!.settings.clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
        assertThat(rendered.single().clean).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun choosingAPaperAndTurning_changeThatPage() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.PaperChosen(PaperSize.A4))
        viewModel.onAction(DocumentAction.TurnRight)
        viewModel.onAction(DocumentAction.TurnRight)
        viewModel.onAction(DocumentAction.TurnLeft)

        val settings = viewModel.now.currentPage!!.settings
        assertThat(settings.paper).isEqualTo(PaperSize.A4)
        assertThat(settings.turn).isEqualTo(1)
    }

    @Test fun aNewPage_takesTheCleanUpAndPaperOfTheLast_butNotItsCornersOrTurn() {
        val viewModel = viewModel()
        tilted.points.forEachIndexed { index, point -> viewModel.onAction(DocumentAction.CornerMoved(index, point)) }
        viewModel.confirmCorners()
        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        viewModel.onAction(DocumentAction.PaperChosen(PaperSize.LETTER))
        viewModel.onAction(DocumentAction.TurnRight)

        viewModel.onAction(DocumentAction.PhotoPicked("second"))

        val settings = viewModel.now.currentPage!!.settings
        assertThat(settings).isEqualTo(PageSettings(clean = CleanMode.GREY, paper = PaperSize.LETTER))
        assertThat(viewModel.now.current).isEqualTo(1)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
    }

    @Test fun aPhotoThatCannotBeRead_saysSo_andKeepsThePages() {
        val viewModel = reviewing()
        photos.importsWork = false

        viewModel.onAction(DocumentAction.PhotoPicked("second"))

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.PHOTO_FAILED)
        assertThat(viewModel.now.pages).hasSize(1)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.busy).isFalse()
    }

    @Test fun takingAPhoto_addsItThroughTheCamera() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)

        viewModel.onAction(DocumentAction.PhotoTaken("shot"))

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("first", "work-shot").inOrder()
    }

    @Test fun backFromTheCamera_returnsToTheReview() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.confirm).isNull()
    }

    @Test fun aPhotoThatFails_saysSo_andStaysOnTheCamera() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)

        viewModel.onAction(DocumentAction.CameraFailed)

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.CAMERA_FAILED)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
    }

    @Test fun aChoiceMadeWhileThePhotoIsStillLoading_isNotLost() {
        val viewModel = reviewing()
        viewModel.addPage()
        val gate = CompletableDeferred<Unit>()
        photos.loadGate = gate
        rendered.clear()

        viewModel.onAction(DocumentAction.PageSelected(0))
        viewModel.onAction(DocumentAction.CleanChosen(CleanMode.GREY))
        gate.complete(Unit)

        assertThat(viewModel.now.photo).isNotNull()
        assertThat(viewModel.now.rendering).isFalse()
        assertThat(rendered.last().clean).isEqualTo(CleanMode.GREY)
    }

    @Test fun movingAPage_followsIt_andStopsAtTheEnds() {
        val viewModel = reviewing()
        viewModel.addPage()

        viewModel.onAction(DocumentAction.MovePageEarlier)
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("from-second", "first").inOrder()
        assertThat(viewModel.now.current).isEqualTo(0)

        viewModel.onAction(DocumentAction.MovePageEarlier)
        assertThat(viewModel.now.current).isEqualTo(0)

        viewModel.onAction(DocumentAction.MovePageLater)
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("first", "from-second").inOrder()
        viewModel.onAction(DocumentAction.MovePageLater)
        assertThat(viewModel.now.current).isEqualTo(1)
    }

    @Test fun deletingAPage_asksFirst() {
        val viewModel = reviewing()
        viewModel.addPage()

        viewModel.onAction(DocumentAction.DeletePage)
        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DELETE_PAGE)
        assertThat(viewModel.now.pages).hasSize(2)

        viewModel.onAction(DocumentAction.Confirmed)
        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("first")
        assertThat(viewModel.now.current).isEqualTo(0)
        assertThat(photos.deleted).containsExactly("from-second")
        assertThat(viewModel.now.result).isNotNull()
    }

    @Test fun deletingTheOnlyPage_reopensTheCamera() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.DeletePage)

        viewModel.onAction(DocumentAction.Confirmed)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages).isEmpty()
        assertThat(viewModel.now.closed).isFalse()
    }

    @Test fun startingANewScan_removesThePhotosOfTheLastOne() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(photos.deleted).containsExactly("first")
    }

    @Test fun backFromAnUnsavedReview_asksBeforeDiscarding() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.BackPressed)
        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
        assertThat(viewModel.now.closed).isFalse()

        viewModel.onAction(DocumentAction.Confirmed)
        assertThat(viewModel.now.closed).isTrue()
    }

    @Test fun backAfterSaving_startsANewScanWithoutAsking() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isFalse()
        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages).isEmpty()
    }

    @Test fun aChangeAfterSaving_asksAgain() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))
        viewModel.onAction(DocumentAction.TurnRight)

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
    }

    @Test fun savingOnePictureOfSeveralPages_stillAsksOnBack() {
        val viewModel = reviewing()
        viewModel.addPage()
        viewModel.onAction(DocumentAction.SavePictureTo("uri", PictureFormat.PNG))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
        assertThat(viewModel.now.closed).isFalse()
    }

    @Test fun savingThePictureOfTheOnlyPage_startsANewScanOnBack() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePictureTo("uri", PictureFormat.JPEG))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isFalse()
        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages).isEmpty()
    }

    @Test fun sharing_stillAsksOnBack_sinceTheSheetMayHaveBeenDismissed() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SharePdf)
        viewModel.onAction(DocumentAction.ShareHandled)

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
    }

    @Test fun backWhileAFileIsBeingWritten_doesNothing() {
        val viewModel = reviewing()
        val gate = CompletableDeferred<Unit>()
        files.gate = gate
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))
        assertThat(viewModel.now.busy).isTrue()

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.closed).isFalse()
        gate.complete(Unit)
        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVED)
    }

    @Test fun aPhotoArrivingAfterTheCameraWasLeft_isDropped() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)
        viewModel.onAction(DocumentAction.BackPressed)

        viewModel.onAction(DocumentAction.PhotoTaken("shot"))

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.pages).hasSize(1)
        assertThat(photos.deleted).containsExactly("shot")
    }

    @Test fun aCameraFailureAfterTheCameraWasLeft_saysNothing() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)
        viewModel.onAction(DocumentAction.BackPressed)

        viewModel.onAction(DocumentAction.CameraFailed)

        assertThat(viewModel.now.message).isNull()
    }

    @Test fun savingPicturesToAFolder_writesEveryPageInOrder() {
        val viewModel = reviewing()
        viewModel.addPage()

        viewModel.onAction(DocumentAction.SavePicturesTo("folder", PictureFormat.JPEG))

        val (folder, pages, format) = files.folders.single()
        assertThat(folder).isEqualTo("folder")
        assertThat(pages.map { it.photoPath }).containsExactly("first", "from-second").inOrder()
        assertThat(format).isEqualTo(PictureFormat.JPEG)
        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVED)
    }

    @Test fun savingPicturesOfEveryPage_startsANewScanOnBack() {
        val viewModel = reviewing()
        viewModel.addPage()
        viewModel.onAction(DocumentAction.SavePicturesTo("folder", PictureFormat.PNG))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isFalse()
        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CAMERA)
        assertThat(viewModel.now.pages).isEmpty()
    }

    @Test fun picturesThatCouldNotBeSaved_sayNoAndKeepAsking() {
        val viewModel = reviewing()
        viewModel.addPage()
        files.works = false

        viewModel.onAction(DocumentAction.SavePicturesTo("folder", PictureFormat.PNG))
        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVE_FAILED)
        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
        assertThat(files.discarded).isEmpty()
    }

    @Test fun savingAPdf_writesEveryPageInOrder() {
        val viewModel = reviewing()
        viewModel.addPage()

        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        val (uri, pages) = files.pdfs.single()
        assertThat(uri).isEqualTo("uri")
        assertThat(pages.map { it.photoPath }).containsExactly("first", "from-second").inOrder()
        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVED)
        assertThat(viewModel.now.busy).isFalse()
    }

    @Test fun aFailedSave_saysSo_removesTheEmptyFile_andStillAsksOnBack() {
        val viewModel = reviewing()
        files.works = false

        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVE_FAILED)
        assertThat(files.discarded).containsExactly("uri")
        viewModel.onAction(DocumentAction.BackPressed)
        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
    }

    @Test fun savingAPicture_writesOnlyTheCurrentPage() {
        val viewModel = reviewing()
        viewModel.addPage()

        viewModel.onAction(DocumentAction.SavePictureTo("uri", PictureFormat.PNG))

        assertThat(files.pictures.single()).isEqualTo(Triple("uri", "from-second", PictureFormat.PNG))
        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SAVED)
    }

    @Test fun sharing_handsOverTheFileOnce() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.SharePdf)
        assertThat(viewModel.now.shareUri).isEqualTo("content://shared")

        viewModel.onAction(DocumentAction.ShareHandled)
        assertThat(viewModel.now.shareUri).isNull()
    }

    @Test fun aFailedShare_saysSo() {
        val viewModel = reviewing()
        files.works = false

        viewModel.onAction(DocumentAction.SharePdf)

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.SHARE_FAILED)
    }

    @Test fun aPageThatCannotBeRendered_saysSo() {
        val viewModel = viewModel()
        renderWorks = false

        viewModel.confirmCorners()

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.PAGE_FAILED)
        assertThat(viewModel.now.rendering).isFalse()
    }

    @Test fun aPhotoThatCannotBeLoaded_saysSo() {
        photos.loadsWork = false

        val viewModel = viewModel()

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.PAGE_FAILED)
        assertThat(viewModel.now.photo).isNull()
    }

    @Test fun cornersCannotBeConfirmedBeforeThePhotoIsThere() {
        photos.loadsWork = false
        val viewModel = viewModel()

        viewModel.confirmCorners()

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.CORNERS)
    }

    @Test fun messagesAreShownOnce() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        viewModel.onAction(DocumentAction.MessageShown)

        assertThat(viewModel.now.message).isNull()
    }

    @Test fun leavingForGood_removesThePhotosOfThisScan() {
        val viewModel = reviewing()
        viewModel.addPage()
        val store = ViewModelStore()
        store.put("document", viewModel)

        store.clear()

        assertThat(photos.deleted).containsExactly("first", "from-second")
    }
}
