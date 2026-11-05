package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.DocumentFiles
import io.github.martindavinci.lunaris.scanner.document.DocumentInbox
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PagePoint
import io.github.martindavinci.lunaris.scanner.document.PageRendering
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import io.github.martindavinci.lunaris.scanner.document.PageSize
import io.github.martindavinci.lunaris.scanner.document.PaperSize
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
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

        override suspend fun imported(uri: String) = if (importsWork) ImportedPhoto("from-$uri", PageSize(40, 20)) else null

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

        override suspend fun discard(uri: String) {
            discarded += uri
        }

        override suspend fun cachePdfForSharing(pages: List<DocumentPage>) = if (works) "content://shared" else null
    }

    private val photos = FakePhotos()
    private val files = FakeFiles()
    private val inbox = DocumentInbox()
    private val rendered = mutableListOf<PageSettings>()
    private var renderWorks = true
    private val rendering = PageRendering { _, settings, _, _ ->
        rendered += settings
        if (renderWorks) PagePixels(IntArray(1), 1, 1) else null
    }
    private val tilted = PageCorners(PagePoint(0.1f, 0.2f), PagePoint(0.9f, 0.1f), PagePoint(0.8f, 0.9f), PagePoint(0.2f, 0.8f))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(first: Boolean = true): DocumentViewModel {
        if (first) inbox.put(ImportedPhoto("first", PageSize(40, 20)))
        return DocumentViewModel(inbox, photos, files, rendering)
    }

    private fun DocumentViewModel.confirmCorners() = onAction(DocumentAction.CornersDone)

    private fun reviewing() = viewModel().also { it.confirmCorners() }

    private fun DocumentViewModel.addPage(uri: String = "second") {
        onAction(DocumentAction.PhotoPicked(uri))
        confirmCorners()
    }

    private val DocumentViewModel.now get() = state.value

    @Test fun nothingInTheInbox_closes() {
        assertThat(viewModel(first = false).now.closed).isTrue()
    }

    @Test fun startsOnTheCornersOfThePhotoFromTheInbox() {
        val state = viewModel().now

        assertThat(state.step).isEqualTo(DocumentStep.CORNERS)
        assertThat(state.corners).isEqualTo(PageCorners.START)
        assertThat(state.photo).isNotNull()
        assertThat(state.pages.map { it.photoPath }).containsExactly("first")
        assertThat(inbox.take()).isNull()
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

    @Test fun backFromTheOnlyNewPage_dropsItAndCloses() {
        val viewModel = viewModel()

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isTrue()
        assertThat(photos.deleted).containsExactly("first")
    }

    @Test fun backFromASecondNewPage_returnsToTheFirst() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.PhotoPicked("second"))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
        assertThat(viewModel.now.pages.map { it.photoPath }).containsExactly("first")
        assertThat(viewModel.now.current).isEqualTo(0)
        assertThat(viewModel.now.result).isNotNull()
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

    @Test fun aCameraThatFails_saysSo_andReturnsToTheReview() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.OpenCamera)

        viewModel.onAction(DocumentAction.CameraFailed)

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.CAMERA_FAILED)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
    }

    @Test fun selectingAPage_showsIt() {
        val viewModel = reviewing()
        viewModel.addPage()
        rendered.clear()

        viewModel.onAction(DocumentAction.PageSelected(0))

        assertThat(viewModel.now.current).isEqualTo(0)
        assertThat(rendered).hasSize(1)
        assertThat(viewModel.now.result).isNotNull()
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

    @Test fun deletingTheOnlyPage_closes() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.DeletePage)
        viewModel.onAction(DocumentAction.Confirmed)

        assertThat(viewModel.now.closed).isTrue()
    }

    @Test fun dismissingTheQuestion_changesNothing() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.DeletePage)

        viewModel.onAction(DocumentAction.Dismissed)

        assertThat(viewModel.now.confirm).isNull()
        assertThat(viewModel.now.pages).hasSize(1)
    }

    @Test fun backFromAnUnsavedReview_asksBeforeDiscarding() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.BackPressed)
        assertThat(viewModel.now.confirm).isEqualTo(DocumentConfirm.DISCARD)
        assertThat(viewModel.now.closed).isFalse()

        viewModel.onAction(DocumentAction.Confirmed)
        assertThat(viewModel.now.closed).isTrue()
    }

    @Test fun backAfterSaving_leavesWithoutAsking() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePdfTo("uri"))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isTrue()
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

    @Test fun savingThePictureOfTheOnlyPage_leavesWithoutAsking() {
        val viewModel = reviewing()
        viewModel.onAction(DocumentAction.SavePictureTo("uri", PictureFormat.JPEG))

        viewModel.onAction(DocumentAction.BackPressed)

        assertThat(viewModel.now.closed).isTrue()
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

    @Test fun aCameraPermissionRefused_saysSo() {
        val viewModel = reviewing()

        viewModel.onAction(DocumentAction.CameraRefused)

        assertThat(viewModel.now.message).isEqualTo(DocumentMessage.CAMERA_FAILED)
        assertThat(viewModel.now.step).isEqualTo(DocumentStep.REVIEW)
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
