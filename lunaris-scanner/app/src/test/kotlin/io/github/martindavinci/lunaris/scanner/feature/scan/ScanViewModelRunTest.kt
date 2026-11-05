package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
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
class ScanViewModelRunTest {
    @get:Rule val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewModels = mutableListOf<ScanViewModel>()
    private val currentResult = CurrentResult()
    private val recorded = mutableListOf<ScannedCode>()
    private var nowMillis = 0L
    private val one = ScannedCode("one", CodeFormat.QR_CODE)
    private val two = ScannedCode("two", CodeFormat.QR_CODE)
    private val three = ScannedCode("three", CodeFormat.QR_CODE)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    private fun viewModel(picked: ScannedCode? = null) = ScanViewModel(
        decoder = CodeDecoder(),
        imageDecoder = { picked },
        scanRecorder = { recorded += it },
        currentResult = currentResult,
        settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        ),
        clock = { nowMillis },
    ).also {
        viewModels += it
        it.onAction(ScanAction.CodeChosen)
    }

    private fun ScanViewModel.read(code: ScannedCode) = onAction(ScanAction.CodeDetected(code))

    private fun pass(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private val ScanViewModel.now get() = state.value
    private val ScanViewModel.codes get() = now.session.map { it.code }

    @Test fun aCode_goesOnTheCard_withoutLeavingTheCamera() {
        val viewModel = viewModel()

        viewModel.read(one)

        assertThat(viewModel.codes).containsExactly(one)
        assertThat(viewModel.now.choosing).isFalse()
        assertThat(currentResult.code.value).isNull()
        assertThat(recorded).containsExactly(one)
    }

    @Test fun readingPauses_forAWhile() {
        val viewModel = viewModel()

        viewModel.read(one)
        assertThat(viewModel.now.readingPaused).isTrue()

        pass(READ_PAUSE_MILLIS - 1)
        assertThat(viewModel.now.readingPaused).isTrue()

        pass(1)
        assertThat(viewModel.now.readingPaused).isFalse()
    }

    @Test fun whileTheCardIsHeld_readingStaysPaused() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.CardHeld(true))
        pass(READ_PAUSE_MILLIS * 3)
        assertThat(viewModel.now.readingPaused).isTrue()

        viewModel.onAction(ScanAction.CardHeld(false))
        assertThat(viewModel.now.readingPaused).isFalse()
    }

    @Test fun lettingGoEarly_doesNotCutThePauseShort() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.CardHeld(true))
        viewModel.onAction(ScanAction.CardHeld(false))

        assertThat(viewModel.now.readingPaused).isTrue()
    }

    @Test fun aCodeArrivingWhileTheCardIsHeld_doesNotChangeWhatIsAboutToBeOpened() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)
        viewModel.onAction(ScanAction.CardHeld(true))

        viewModel.read(two)

        assertThat(viewModel.codes).containsExactly(one)
        assertThat(recorded).containsExactly(one)
    }

    @Test fun aCodeDroppedUnderAFinger_isReadOnceTheFingerLifts() {
        val viewModel = viewModel()
        viewModel.analyzer.resume()
        viewModel.onAction(ScanAction.CardHeld(true))

        viewModel.analyzer.deliver(two)
        assertThat(viewModel.now.session).isEmpty()

        viewModel.onAction(ScanAction.CardHeld(false))
        viewModel.analyzer.resume()
        viewModel.analyzer.deliver(two)

        assertThat(viewModel.codes).containsExactly(two)
    }

    @Test fun aPickedImageReadWhileTheCardIsHeld_isNotLost() {
        val viewModel = viewModel(picked = two)
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)
        viewModel.onAction(ScanAction.CardHeld(true))

        viewModel.onAction(ScanAction.ImagePicked("content://image"))

        assertThat(viewModel.codes).containsExactly(two, one).inOrder()
    }

    @Test fun theNextCode_takesTheCard_andTheFirstGoesInTheList() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)

        viewModel.read(two)

        assertThat(viewModel.codes).containsExactly(two, one).inOrder()
        assertThat(viewModel.now.readingPaused).isTrue()
    }

    @Test fun eachReading_countsForTheAnimation() {
        val viewModel = viewModel()
        viewModel.read(one)
        val first = viewModel.now.readings
        pass(READ_PAUSE_MILLIS)

        viewModel.read(two)

        assertThat(viewModel.now.readings).isGreaterThan(first)
    }

    @Test fun theSameCodeAgain_doesNotCountTwice_butComesBackOnTheCard() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)
        viewModel.read(two)
        pass(READ_PAUSE_MILLIS)

        viewModel.read(one)

        assertThat(viewModel.codes).containsExactly(one, two).inOrder()
        assertThat(recorded).containsExactly(one, two).inOrder()
    }

    @Test fun aSecondCodeOfAnotherFormat_isAnotherCode() {
        val viewModel = viewModel()
        viewModel.read(ScannedCode("123", CodeFormat.QR_CODE))
        pass(READ_PAUSE_MILLIS)

        viewModel.read(ScannedCode("123", CodeFormat.CODE_128))

        assertThat(viewModel.now.session).hasSize(2)
    }

    @Test fun aPickedImage_goesOnTheCardToo() {
        val viewModel = viewModel(picked = one)

        viewModel.onAction(ScanAction.ImagePicked("content://image"))

        assertThat(viewModel.codes).containsExactly(one)
        assertThat(currentResult.code.value).isNull()
    }

    @Test fun askingForDetails_setsTheCodeForTheFullPage() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.DetailsAsked(one))

        assertThat(currentResult.code.value).isEqualTo(one)
    }

    @Test fun theListPausesReading_whileItIsOpen() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)

        viewModel.onAction(ScanAction.ListOpened)
        assertThat(viewModel.now.listOpen).isTrue()
        assertThat(viewModel.now.readingPaused).isTrue()

        viewModel.onAction(ScanAction.ListClosed)
        assertThat(viewModel.now.readingPaused).isFalse()
    }

    @Test fun aCodeArrivingAsTheListOpens_isLeftOut() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)
        viewModel.onAction(ScanAction.ListOpened)

        viewModel.read(two)

        assertThat(viewModel.codes).containsExactly(one)
    }

    @Test fun backToTheChoice_startsTheNextSessionEmpty() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.ChoiceAsked)
        viewModel.onAction(ScanAction.CodeChosen)

        assertThat(viewModel.now.session).isEmpty()
        assertThat(viewModel.now.readingPaused).isFalse()
    }

    @Test fun aRequestForTheChoice_startsTheNextSessionEmpty() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.Requested(ScanRequest(id = 1, codes = false)))

        assertThat(viewModel.now.session).isEmpty()
    }

    @Test fun comingBackWithinFiveMinutes_findsTheCodesStillThere() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.ScreenHidden)
        nowMillis += 4 * 60_000
        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.codes).containsExactly(one)
    }

    @Test fun comingBackAfterFiveMinutes_findsThemGone() {
        val viewModel = viewModel()
        viewModel.read(one)

        viewModel.onAction(ScanAction.ScreenHidden)
        nowMillis += 5 * 60_000
        viewModel.onAction(ScanAction.ScreenShown)

        assertThat(viewModel.now.choosing).isTrue()
        assertThat(viewModel.now.session).isEmpty()
    }

    @Test fun lookingAtAFullPage_keepsTheCodes_theOpenList_andEndsThePause() {
        val viewModel = viewModel()
        viewModel.read(one)
        pass(READ_PAUSE_MILLIS)
        viewModel.read(two)
        viewModel.onAction(ScanAction.CardHeld(true))
        viewModel.onAction(ScanAction.ListOpened)

        viewModel.onAction(ScanAction.ScreenLeft)

        assertThat(viewModel.codes).containsExactly(two, one).inOrder()
        assertThat(viewModel.now.cardHeld).isFalse()
        assertThat(viewModel.now.listOpen).isTrue()
    }

    @Test fun comingBackFromAFullPage_readsAgain() {
        val viewModel = viewModel()
        viewModel.read(three)
        viewModel.onAction(ScanAction.CardHeld(true))

        viewModel.onAction(ScanAction.ScreenLeft)

        assertThat(viewModel.now.readingPaused).isFalse()
    }
}
