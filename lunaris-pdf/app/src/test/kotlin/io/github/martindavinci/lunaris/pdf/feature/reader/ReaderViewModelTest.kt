package io.github.martindavinci.lunaris.pdf.feature.reader

import android.webkit.WebView
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import io.github.martindavinci.lunaris.pdf.recents.DocumentThumbnails
import io.github.martindavinci.lunaris.pdf.recents.ReadingSpot
import io.github.martindavinci.lunaris.pdf.recents.RecentVisit
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import io.github.martindavinci.lunaris.pdf.url.UrlWarning
import io.github.martindavinci.lunaris.pdf.viewer.OutlineItem
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private class FakeChannel : ViewerChannel {
        val sent = mutableListOf<ViewerCommand>()
        override val events = MutableSharedFlow<ViewerEvent>(extraBufferCapacity = 16)
        override fun send(command: ViewerCommand) {
            sent += command
        }
        override fun attach(view: WebView) = true
        override fun detach() = Unit
        override fun report(event: ViewerEvent) {
            events.tryEmit(event)
        }
    }

    private class FakeDocuments : DocumentActions {
        var saveWorks = true
        var shareUri: String? = "content://share/a.pdf"
        val saved = mutableListOf<Pair<String, String>>()
        override suspend fun saveCopy(from: String, to: String): Boolean {
            saved += from to to
            return saveWorks
        }
        override suspend fun prepareShare(from: String, name: String): String? = shareUri
    }

    private class FakeThumbnails : DocumentThumbnails {
        var exists = false
        val saved = mutableListOf<Pair<String, Int>>()
        override suspend fun has(uri: String) = exists
        override suspend fun save(uri: String, jpeg: ByteArray) {
            saved += uri to jpeg.size
        }
    }

    private val channel = FakeChannel()
    private val current = CurrentDocument()
    private val savedState = SavedStateHandle()
    private val night = MutableStateFlow(false)
    private val topButton = MutableStateFlow(true)
    private val touched = mutableListOf<Pair<String, String>>()
    private val visits = mutableListOf<RecentVisit>()
    private val spots = mutableListOf<ReadingSpot>()
    private val thumbnails = FakeThumbnails()
    private val positions = mutableListOf<Triple<String, Int, String>>()
    private val documents = FakeDocuments()
    private lateinit var dispatcher: TestDispatcher

    @Before fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        current.set(OpenDocument("content://x/a.pdf", "a.pdf"))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = ReaderViewModel(
        viewer = channel,
        currentDocument = current,
        savedState = state,
        nightPages = night,
        setNightPages = { night.value = it },
        topButtonEnabled = topButton,
        touchRecent = { visit ->
            touched += visit.uri to visit.name
            visits += visit
        },
        savePosition = { spot ->
            positions += Triple(spot.uri, spot.page, spot.scale)
            spots += spot
        },
        thumbnails = thumbnails,
        documents = documents,
    )

    private fun emit(event: ViewerEvent) {
        channel.events.tryEmit(event)
    }

    private fun ready(pages: Int = 10) {
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.Loaded(pages, emptyList()))
    }

    @Test fun onFirstPage_noTopButton() {
        val vm = viewModel()
        ready()
        assertThat(vm.state.value.showTopButton).isFalse()
    }

    @Test fun afterLeavingThePage_topButtonShows_andGoesToPageOne() {
        val vm = viewModel()
        ready()
        emit(ViewerEvent.PageChanged(2, "page-width"))
        assertThat(vm.state.value.showTopButton).isFalse()
        emit(ViewerEvent.PageChanged(3, "page-width"))
        assertThat(vm.state.value.showTopButton).isTrue()
        vm.onAction(ReaderAction.BackToTop)
        assertThat(channel.sent).contains(ViewerCommand.GoToPage(1))
    }

    @Test fun topButtonTurnedOff_doesNotShowOnAnyPage() {
        val vm = viewModel()
        ready()
        emit(ViewerEvent.PageChanged(5, "page-width"))
        assertThat(vm.state.value.showTopButton).isTrue()
        topButton.value = false
        assertThat(vm.state.value.showTopButton).isFalse()
        topButton.value = true
        assertThat(vm.state.value.showTopButton).isTrue()
    }

    @Test fun ready_opensTheDocumentWhereItWasLeft() {
        current.set(OpenDocument("content://x/a.pdf", "a.pdf", page = 7, scale = "1.5"))
        viewModel()
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.Open(null, 7, "1.5"))
    }

    @Test fun storedScaleThatMakesNoSense_fallsBackToFittingTheWidth() {
        current.set(OpenDocument("content://x/a.pdf", "a.pdf", scale = "<b>"))
        viewModel()
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.Open(null, 1, OpenDocument.DEFAULT_SCALE))
    }

    @Test fun tap_togglesTheBars() {
        val vm = viewModel()
        assertThat(vm.state.value.chromeVisible).isTrue()
        emit(ViewerEvent.Tapped)
        assertThat(vm.state.value.chromeVisible).isFalse()
        emit(ViewerEvent.Tapped)
        assertThat(vm.state.value.chromeVisible).isTrue()
    }

    @Test fun tap_keepsTheBarsWhileSearching() {
        val vm = viewModel()
        vm.onAction(ReaderAction.OpenSearch)
        emit(ViewerEvent.Tapped)
        assertThat(vm.state.value.chromeVisible).isTrue()
    }

    @Test fun loadFailed_showsTheMessageAndStopsLoading() {
        val vm = viewModel()
        assertThat(vm.state.value.loading).isTrue()
        emit(ViewerEvent.LoadFailed)
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.error).isEqualTo(ReaderError.CANNOT_OPEN)
    }

    @Test fun noDocumentAtAll_showsTheMessage() {
        current.set(null)
        val vm = viewModel()
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.error).isEqualTo(ReaderError.CANNOT_OPEN)
    }

    @Test fun pageThatNeverLoads_endsInTheMessage() {
        Dispatchers.resetMain()
        val standard = StandardTestDispatcher()
        Dispatchers.setMain(standard)
        val vm = viewModel()
        standard.scheduler.runCurrent()
        standard.scheduler.advanceTimeBy(ReaderViewModel.READY_TIMEOUT_MILLIS + 1)
        standard.scheduler.runCurrent()
        assertThat(vm.state.value.error).isEqualTo(ReaderError.CANNOT_OPEN)
    }

    @Test fun slowDocument_isNotCutOffOnceThePageIsReady() {
        Dispatchers.resetMain()
        val standard = StandardTestDispatcher()
        Dispatchers.setMain(standard)
        val vm = viewModel()
        standard.scheduler.runCurrent()
        emit(ViewerEvent.Ready)
        standard.scheduler.runCurrent()
        standard.scheduler.advanceTimeBy(ReaderViewModel.READY_TIMEOUT_MILLIS * 2)
        standard.scheduler.runCurrent()
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.loading).isTrue()
    }

    @Test fun firstPasswordRequest_doesNotSayItWasWrong() {
        val vm = viewModel()
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        assertThat(vm.state.value.passwordPrompt).isEqualTo(PasswordPrompt(wrong = false))
        assertThat(vm.state.value.loading).isFalse()
    }

    @Test fun wrongPassword_asksAgainSaysWhy() {
        val vm = viewModel()
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        vm.onAction(ReaderAction.SubmitPassword("nope"))
        assertThat(vm.state.value.passwordPrompt).isNull()
        assertThat(channel.sent).contains(ViewerCommand.Open("nope", 1, OpenDocument.DEFAULT_SCALE))
        emit(ViewerEvent.PasswordNeeded(wrong = true))
        assertThat(vm.state.value.passwordPrompt).isEqualTo(PasswordPrompt(wrong = true))
    }

    @Test fun passwordIsNotInSavedState() {
        val vm = viewModel()
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        vm.onAction(ReaderAction.SubmitPassword("secret-word"))
        emit(ViewerEvent.PageChanged(3, "page-width"))
        savedState.keys().forEach { key ->
            assertThat(savedState.get<Any?>(key).toString()).doesNotContain("secret-word")
        }
        assertThat(vm.state.value.toString()).doesNotContain("secret-word")
    }

    @Test fun passwordIsAskedAgainAfterRecreation() {
        viewModel()
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        current.set(null)
        channel.sent.clear()
        viewModel(savedState)
        emit(ViewerEvent.Ready)
        assertThat(channel.sent.filterIsInstance<ViewerCommand.Open>().last().password).isNull()
    }

    @Test fun http_link_needsConfirmationAndCarriesItsWarnings() {
        val vm = viewModel()
        emit(ViewerEvent.LinkTapped("http://example.com"))
        val link = checkNotNull(vm.state.value.pendingLink)
        assertThat(link.url).isEqualTo("http://example.com")
        assertThat(link.canOpen).isTrue()
        assertThat(link.warnings).containsExactly(UrlWarning.NOT_HTTPS)
    }

    @Test fun javascriptLink_cannotBeOpened() {
        val vm = viewModel()
        emit(ViewerEvent.LinkTapped("javascript:alert(1)"))
        assertThat(checkNotNull(vm.state.value.pendingLink).canOpen).isFalse()
    }

    @Test fun intentLink_cannotBeOpened() {
        val vm = viewModel()
        emit(ViewerEvent.LinkTapped("intent://x#Intent;scheme=https;end"))
        assertThat(checkNotNull(vm.state.value.pendingLink).canOpen).isFalse()
    }

    @Test fun linkWithBidiCharacters_showsThemAndCannotBeOpened() {
        val vm = viewModel()
        emit(ViewerEvent.LinkTapped("https://exa‮mple.com"))
        val link = checkNotNull(vm.state.value.pendingLink)
        assertThat(link.canOpen).isFalse()
        assertThat(link.shownUrl).contains("\\u202E")
    }

    @Test fun dismissingTheLink_clearsIt() {
        val vm = viewModel()
        emit(ViewerEvent.LinkTapped("https://example.com"))
        vm.onAction(ReaderAction.DismissLink)
        assertThat(vm.state.value.pendingLink).isNull()
    }

    @Test fun goToPage_isClampedToTheDocument() {
        val vm = viewModel()
        ready(pages = 10)
        vm.onAction(ReaderAction.GoToPage(99))
        assertThat(channel.sent).contains(ViewerCommand.GoToPage(10))
        vm.onAction(ReaderAction.GoToPage(-5))
        assertThat(channel.sent).contains(ViewerCommand.GoToPage(1))
    }

    @Test fun searchCounts_followFindResult() {
        val vm = viewModel()
        vm.onAction(ReaderAction.OpenSearch)
        emit(ViewerEvent.FindResult(2, 9, done = false))
        assertThat(vm.state.value.search).isEqualTo(SearchState(current = 2, total = 9, searching = true))
        emit(ViewerEvent.FindResult(2, 9, done = true))
        assertThat(vm.state.value.search).isEqualTo(SearchState(current = 2, total = 9, searching = false))
    }

    @Test fun nextAndPrevious_searchTheSameQuery() {
        val vm = viewModel()
        vm.onAction(ReaderAction.OpenSearch)
        vm.onAction(ReaderAction.SubmitSearch("moon"))
        vm.onAction(ReaderAction.FindNext)
        vm.onAction(ReaderAction.FindPrevious)
        assertThat(channel.sent).containsAtLeast(
            ViewerCommand.Find("moon", false),
            ViewerCommand.Find("moon", true),
        )
        assertThat(vm.state.value.search?.query).isEqualTo("moon")
    }

    @Test fun blankQuery_searchesNothing() {
        val vm = viewModel()
        vm.onAction(ReaderAction.OpenSearch)
        vm.onAction(ReaderAction.SubmitSearch("  "))
        vm.onAction(ReaderAction.FindNext)
        assertThat(channel.sent.filterIsInstance<ViewerCommand.Find>()).isEmpty()
    }

    @Test fun closingTheSearch_clearsTheHighlights() {
        val vm = viewModel()
        vm.onAction(ReaderAction.OpenSearch)
        vm.onAction(ReaderAction.CloseSearch)
        assertThat(vm.state.value.search).isNull()
        assertThat(channel.sent).contains(ViewerCommand.CloseFind)
    }

    @Test fun afterRecreation_resumesOnTheSamePage() {
        viewModel()
        ready()
        emit(ViewerEvent.PageChanged(6, "2.0"))
        current.set(null)
        channel.sent.clear()
        val vm = viewModel(savedState)
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.Open(null, 6, "2.0"))
        assertThat(vm.state.value.uri).isEqualTo("content://x/a.pdf")
        assertThat(vm.state.value.name).isEqualTo("a.pdf")
    }

    @Test fun laterLoaded_bringsTheOutlineWithoutLosingThePage() {
        val vm = viewModel()
        ready()
        emit(ViewerEvent.PageChanged(4, "page-width"))
        val outline = listOf(OutlineItem("One", 1, 0))
        emit(ViewerEvent.Loaded(10, outline))
        assertThat(vm.state.value.outline).isEqualTo(outline)
        assertThat(vm.state.value.page).isEqualTo(4)
    }

    @Test fun nightSetting_reachesTheViewerWhenReadyAndWhenItChanges() {
        val vm = viewModel()
        night.value = true
        assertThat(vm.state.value.night).isTrue()
        assertThat(channel.sent).isEmpty()
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.SetNight(true))
        channel.sent.clear()
        night.value = false
        assertThat(channel.sent).containsExactly(ViewerCommand.SetNight(false))
    }

    @Test fun toggleNight_changesTheSetting() {
        val vm = viewModel()
        vm.onAction(ReaderAction.ToggleNight)
        assertThat(night.value).isTrue()
        assertThat(vm.state.value.night).isTrue()
    }

    @Test fun openedDocument_joinsTheRecentsOnce() {
        viewModel()
        ready()
        emit(ViewerEvent.Loaded(10, listOf(OutlineItem("One", 1, 0))))
        assertThat(touched).containsExactly("content://x/a.pdf" to "a.pdf")
    }

    @Test fun fileThatDoesNotOpen_staysOutOfTheRecents() {
        viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.LoadFailed)
        assertThat(touched).isEmpty()
    }

    @Test fun position_isSavedOnceTheScrollingStops() {
        Dispatchers.resetMain()
        val standard = StandardTestDispatcher()
        Dispatchers.setMain(standard)
        viewModel()
        standard.scheduler.runCurrent()
        emit(ViewerEvent.PageChanged(2, "1"))
        standard.scheduler.runCurrent()
        standard.scheduler.advanceTimeBy(100)
        emit(ViewerEvent.PageChanged(3, "1"))
        standard.scheduler.runCurrent()
        standard.scheduler.advanceTimeBy(ReaderViewModel.SAVE_POSITION_DELAY_MILLIS + 1)
        standard.scheduler.runCurrent()
        assertThat(positions).containsExactly(Triple("content://x/a.pdf", 3, "1"))
    }

    @Test fun savedCopy_saysSo() {
        val vm = viewModel()
        vm.onAction(ReaderAction.SaveCopyTo("content://x/copy.pdf"))
        assertThat(documents.saved).containsExactly("content://x/a.pdf" to "content://x/copy.pdf")
        assertThat(vm.state.value.notice).isEqualTo(ReaderNotice.SAVED)
        vm.onAction(ReaderAction.NoticeShown)
        assertThat(vm.state.value.notice).isNull()
    }

    @Test fun failedCopy_saysSo() {
        documents.saveWorks = false
        val vm = viewModel()
        vm.onAction(ReaderAction.SaveCopyTo("content://x/copy.pdf"))
        assertThat(vm.state.value.notice).isEqualTo(ReaderNotice.SAVE_FAILED)
    }

    @Test fun share_handsTheFileOver() {
        val vm = viewModel()
        vm.onAction(ReaderAction.Share)
        assertThat(vm.state.value.shareUri).isEqualTo("content://share/a.pdf")
        vm.onAction(ReaderAction.ShareStarted)
        assertThat(vm.state.value.shareUri).isNull()
    }

    @Test fun failedShare_saysSo() {
        documents.shareUri = null
        val vm = viewModel()
        vm.onAction(ReaderAction.Share)
        assertThat(vm.state.value.shareUri).isNull()
        assertThat(vm.state.value.notice).isEqualTo(ReaderNotice.SHARE_FAILED)
    }

    @Test fun documentWithAPassword_cannotBePrinted() {
        val vm = viewModel()
        ready()
        assertThat(vm.state.value.canPrint).isTrue()
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        vm.onAction(ReaderAction.SubmitPassword("luna"))
        assertThat(vm.state.value.canPrint).isFalse()
    }

    @Test fun rendererKilledBySystem_reopensOnTheSamePageWithoutAMessage() {
        val vm = viewModel()
        ready(pages = 10)
        emit(ViewerEvent.PageChanged(6, "1.5"))
        val revision = vm.state.value.viewRevision
        channel.sent.clear()
        emit(ViewerEvent.RendererKilled)
        assertThat(vm.state.value.viewRevision).isEqualTo(revision + 1)
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.loading).isTrue()
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.Open(null, 6, "1.5"))
    }

    @Test fun rendererKilledBySystem_keepsThePasswordOfTheDocument() {
        val vm = viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        vm.onAction(ReaderAction.SubmitPassword("luna"))
        channel.sent.clear()
        emit(ViewerEvent.RendererKilled)
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.Open("luna", 1, OpenDocument.DEFAULT_SCALE))
    }

    private val chapters = listOf(OutlineItem("One", 1, 0), OutlineItem("Part", 2, 1), OutlineItem("Two", 8, 0))

    @Test fun topButton_hidesWhileGoingDown() {
        val vm = viewModel()
        ready()
        emit(ViewerEvent.PageChanged(5, "page-width"))
        emit(ViewerEvent.ScrollChanged(moving = true, down = true))
        assertThat(vm.state.value.showTopButton).isFalse()
        emit(ViewerEvent.ScrollChanged(moving = false, down = false))
        assertThat(vm.state.value.showTopButton).isTrue()
    }

    @Test fun pageIndicator_showsOnlyWhenTheBarsAreHiddenAndThePageMoves() {
        val vm = viewModel()
        ready()
        emit(ViewerEvent.ScrollChanged(moving = true, down = true))
        assertThat(vm.state.value.showPageIndicator).isFalse()
        emit(ViewerEvent.Tapped)
        assertThat(vm.state.value.showPageIndicator).isTrue()
        emit(ViewerEvent.ScrollChanged(moving = false, down = false))
        assertThat(vm.state.value.showPageIndicator).isFalse()
    }

    @Test fun chapter_followsThePage() {
        val vm = viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.Loaded(10, chapters))
        emit(ViewerEvent.PageChanged(9, "page-width"))
        assertThat(vm.state.value.chapter).isEqualTo("Two")
        emit(ViewerEvent.PageChanged(3, "page-width"))
        assertThat(vm.state.value.chapter).isEqualTo("One")
    }

    @Test fun recents_hearTheLengthAndTheChapter() {
        val dispatcherScheduler = dispatcher.scheduler
        val vm = viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.Loaded(10, chapters))
        emit(ViewerEvent.PageChanged(9, "page-width"))
        dispatcherScheduler.advanceTimeBy(ReaderViewModel.SAVE_POSITION_DELAY_MILLIS + 1)
        assertThat(visits.single().pages).isEqualTo(10)
        assertThat(visits.single().locked).isFalse()
        assertThat(spots.last().chapter).isEqualTo("Two")
        assertThat(vm.state.value.page).isEqualTo(9)
    }

    @Test fun anOutlineArrivingLate_stillReachesTheRecents() {
        val vm = viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.PageChanged(9, "page-width"))
        emit(ViewerEvent.Loaded(10, emptyList()))
        emit(ViewerEvent.Loaded(10, chapters))
        dispatcher.scheduler.advanceTimeBy(ReaderViewModel.SAVE_POSITION_DELAY_MILLIS + 1)
        assertThat(spots.last().chapter).isEqualTo("Two")
        assertThat(vm.state.value.outline).isEqualTo(chapters)
    }

    @Test fun aFileWithAPassword_keepsItsContentOutOfTheRecents() {
        val vm = viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.PasswordNeeded(wrong = false))
        vm.onAction(ReaderAction.SubmitPassword("secret-word"))
        emit(ViewerEvent.Loaded(10, chapters))
        emit(ViewerEvent.PageChanged(9, "page-width"))
        dispatcher.scheduler.advanceTimeBy(ReaderViewModel.SAVE_POSITION_DELAY_MILLIS + 1)
        assertThat(visits.single().locked).isTrue()
        assertThat(spots.last().chapter).isEmpty()
        assertThat(channel.sent).doesNotContain(ViewerCommand.MakeThumbnail)
    }

    @Test fun thumbnail_isAskedOnceWhenMissing_andSaved() {
        viewModel()
        emit(ViewerEvent.Ready)
        emit(ViewerEvent.Loaded(10, emptyList()))
        emit(ViewerEvent.Loaded(10, chapters))
        assertThat(channel.sent.count { it == ViewerCommand.MakeThumbnail }).isEqualTo(1)
        emit(ViewerEvent.Thumbnail("/9j/4A=="))
        assertThat(thumbnails.saved.single().first).isEqualTo("content://x/a.pdf")
    }

    @Test fun thumbnail_thatExists_isNotMadeAgain() {
        thumbnails.exists = true
        viewModel()
        ready()
        assertThat(channel.sent).doesNotContain(ViewerCommand.MakeThumbnail)
    }

    @Test fun thumbnail_thatIsNotBase64_isIgnored() {
        viewModel()
        ready()
        emit(ViewerEvent.Thumbnail("not base64 at all!"))
        assertThat(thumbnails.saved).isEmpty()
    }

    @Test fun farGoToPage_offersTheWayBack() {
        val vm = viewModel()
        ready(pages = 30)
        emit(ViewerEvent.PageChanged(12, "page-width"))
        vm.onAction(ReaderAction.GoToPage(20))
        assertThat(vm.state.value.jump).isEqualTo(PageJump(12, 20))
        vm.onAction(ReaderAction.ReturnFromJump)
        assertThat(channel.sent.last()).isEqualTo(ViewerCommand.GoToPage(12))
        assertThat(vm.state.value.jump).isEqualTo(PageJump(20, 12, forward = true))
    }

    @Test fun nearGoToPage_offersNothing() {
        val vm = viewModel()
        ready(pages = 30)
        emit(ViewerEvent.PageChanged(12, "page-width"))
        vm.onAction(ReaderAction.GoToPage(13))
        assertThat(vm.state.value.jump).isNull()
    }

    @Test fun readingOn_endsTheWayBack() {
        val vm = viewModel()
        ready(pages = 30)
        emit(ViewerEvent.PageChanged(12, "page-width"))
        vm.onAction(ReaderAction.GoToPage(20))
        emit(ViewerEvent.PageChanged(20, "page-width"))
        assertThat(vm.state.value.jump).isNotNull()
        emit(ViewerEvent.PageChanged(23, "page-width"))
        assertThat(vm.state.value.jump).isNull()
    }

    @Test fun backToTop_andTheScroller_offerTheWayBack() {
        val vm = viewModel()
        ready(pages = 30)
        emit(ViewerEvent.PageChanged(12, "page-width"))
        vm.onAction(ReaderAction.BackToTop)
        assertThat(vm.state.value.jump).isEqualTo(PageJump(12, 1))
        emit(ViewerEvent.Jumped(1, 25))
        assertThat(vm.state.value.jump).isEqualTo(PageJump(1, 25))
    }

    @Test fun closingASearchThatMovedFar_offersTheWayBack() {
        val vm = viewModel()
        ready(pages = 30)
        emit(ViewerEvent.PageChanged(2, "page-width"))
        vm.onAction(ReaderAction.OpenSearch)
        emit(ViewerEvent.PageChanged(14, "page-width"))
        vm.onAction(ReaderAction.CloseSearch)
        assertThat(vm.state.value.jump).isEqualTo(PageJump(2, 14))
    }

    @Test fun search_runsWithTwoLetters_andTheOptions() {
        val vm = viewModel()
        ready()
        vm.onAction(ReaderAction.OpenSearch)
        vm.onAction(ReaderAction.SubmitSearch("m"))
        assertThat(channel.sent.filterIsInstance<ViewerCommand.Find>()).isEmpty()
        vm.onAction(ReaderAction.SubmitSearch("mo"))
        vm.onAction(ReaderAction.ToggleMatchCase)
        vm.onAction(ReaderAction.ToggleWholeWords)
        assertThat(channel.sent.filterIsInstance<ViewerCommand.Find>()).containsExactly(
            ViewerCommand.Find("mo", false),
            ViewerCommand.Find("mo", false, caseSensitive = true),
            ViewerCommand.Find("mo", false, caseSensitive = true, wholeWords = true),
        ).inOrder()
    }

    @Test fun shorteningTheQuery_clearsTheMatches() {
        val vm = viewModel()
        ready()
        vm.onAction(ReaderAction.OpenSearch)
        vm.onAction(ReaderAction.SubmitSearch("mo"))
        emit(ViewerEvent.FindResult(1, 9, done = true, page = 4, onPage = 2))
        assertThat(vm.state.value.search?.page).isEqualTo(4)
        vm.onAction(ReaderAction.SubmitSearch("m"))
        assertThat(channel.sent.last()).isEqualTo(ViewerCommand.CloseFind)
        assertThat(vm.state.value.search?.total).isEqualTo(0)
    }

    @Test fun barSpace_reachesThePage_alsoWhenItIsNotReadyYet() {
        val vm = viewModel()
        vm.onAction(ReaderAction.SetBarSpace(90, 80))
        emit(ViewerEvent.Ready)
        assertThat(channel.sent).contains(ViewerCommand.SetBarSpace(90, 80))
    }

    @Test fun passingAChapter_asksForATick() {
        val vm = viewModel()
        ready()
        val before = vm.state.value.chapterTicks
        emit(ViewerEvent.ChapterCrossed)
        assertThat(vm.state.value.chapterTicks).isEqualTo(before + 1)
    }

}
