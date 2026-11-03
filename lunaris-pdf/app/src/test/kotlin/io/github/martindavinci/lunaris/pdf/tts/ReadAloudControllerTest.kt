package io.github.martindavinci.lunaris.pdf.tts

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAloudControllerTest {
    private class FakeEngine : SpeechEngine {
        val spoken = mutableListOf<String>()
        var lastId: String? = null
        var stops = 0
        override var onSegmentDone: ((String) -> Unit)? = null
        override var onUnavailable: (() -> Unit)? = null
        override fun speak(text: String, utteranceId: String) {
            spoken += text
            lastId = utteranceId
        }
        override fun stop() {
            stops++
        }
        val rates = mutableListOf<Float>()
        val chosenVoices = mutableListOf<String?>()
        override fun setRate(rate: Float) {
            rates += rate
        }
        override fun setVoice(voiceId: String?) {
            chosenVoices += voiceId
        }
        override fun voices(): List<VoiceInfo> = emptyList()
        override fun shutdown() = Unit
        fun finishSegment() = onSegmentDone?.invoke(checkNotNull(lastId))
    }

    private val engine = FakeEngine()
    private val pages = mutableMapOf<Int, String>()
    private val fetched = mutableListOf<Int>()
    private var gate: CompletableDeferred<Unit>? = null
    private val controller = ReadAloudController(
        engine = engine,
        fetchPage = { page ->
            gate?.await()
            fetched += page
            pages[page].orEmpty()
        },
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test fun start_speaksTheCurrentPage() {
        pages[3] = "Page three."
        controller.start(page = 3, pages = 5)
        assertThat(engine.spoken).containsExactly("Page three.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(3))
    }

    @Test fun sentences_areSpokenOneByOne() {
        pages[1] = "First one. Second one."
        controller.start(1, 1)
        assertThat(engine.spoken).containsExactly("First one.")
        engine.finishSegment()
        assertThat(engine.spoken).containsExactly("First one.", "Second one.").inOrder()
    }

    @Test fun reading_movesToTheNextPageWhenDone() {
        pages[1] = "One."
        pages[2] = "Two."
        controller.start(1, 2)
        engine.finishSegment()
        assertThat(engine.spoken).containsExactly("One.", "Two.").inOrder()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
    }

    @Test fun pageWithoutText_skipsAhead() {
        pages[2] = "Two."
        controller.start(1, 3)
        assertThat(engine.spoken).containsExactly("Two.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
    }

    @Test fun wholeDocumentWithoutText_saysSo() {
        controller.start(1, 100)
        assertThat(controller.state.value).isEqualTo(ReadAloudState.NoText(1))
        assertThat(fetched).containsExactly(1, 2, 3).inOrder()
        assertThat(engine.spoken).isEmpty()
    }

    @Test fun shortDocumentWithoutText_saysSoToo() {
        controller.start(1, 2)
        assertThat(controller.state.value).isEqualTo(ReadAloudState.NoText(1))
    }

    @Test fun manyEmptyPagesAfterSomeText_doNotStopIt() {
        pages[1] = "One."
        pages[6] = "Six."
        controller.start(1, 6)
        engine.finishSegment()
        assertThat(engine.spoken).containsExactly("One.", "Six.").inOrder()
    }

    @Test fun lastPageDone_goesIdle() {
        pages[1] = "One."
        controller.start(1, 1)
        engine.finishSegment()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Idle)
    }

    @Test fun noEngine_saysUnavailable() {
        pages[1] = "One."
        controller.start(1, 1)
        engine.onUnavailable?.invoke()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Unavailable)
    }

    @Test fun pauseAndResume_continueFromTheSameSentence() {
        pages[1] = "First one. Second one."
        controller.start(1, 1)
        engine.finishSegment()
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Paused(1))
        assertThat(engine.stops).isAtLeast(1)
        controller.resume()
        assertThat(engine.spoken).containsExactly("First one.", "Second one.", "Second one.").inOrder()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
    }

    @Test fun finishedSegmentFromBeforeAPause_isIgnored() {
        pages[1] = "First one. Second one."
        controller.start(1, 1)
        val stale = checkNotNull(engine.lastId)
        controller.pause()
        engine.onSegmentDone?.invoke(stale)
        assertThat(engine.spoken).containsExactly("First one.")
    }

    @Test fun nextAndPrevious_changePageWithinTheDocument() {
        pages[1] = "One."
        pages[2] = "Two."
        controller.start(1, 2)
        controller.next()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
        controller.next()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
        controller.previous()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
        controller.previous()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
    }

    @Test fun stop_silencesAndGoesIdle() {
        pages[1] = "One."
        controller.start(1, 1)
        controller.stop()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Idle)
        assertThat(engine.stops).isAtLeast(1)
    }

    @Test fun pauseAndResumeBeforeTheTextArrives_readThePage() {
        pages[1] = "One."
        val waiting = CompletableDeferred<Unit>().also { gate = it }
        controller.start(1, 1)
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Paused(1))
        controller.resume()
        waiting.complete(Unit)
        assertThat(engine.spoken).containsExactly("One.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
    }

    @Test fun nextTwiceWhileAPageLoads_movesTwoPages() {
        pages[1] = "One."
        pages[2] = "Two."
        pages[3] = "Three."
        controller.start(1, 3)
        val waiting = CompletableDeferred<Unit>().also { gate = it }
        controller.next()
        controller.next()
        waiting.complete(Unit)
        assertThat(engine.spoken).containsExactly("One.", "Three.").inOrder()
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(3))
    }

    @Test fun previousWhileAPageLoads_movesFromThatPage() {
        pages[1] = "One."
        pages[2] = "Two."
        pages[3] = "Three."
        controller.start(2, 3)
        val waiting = CompletableDeferred<Unit>().also { gate = it }
        controller.next()
        controller.previous()
        waiting.complete(Unit)
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
    }

    @Test fun sentence_followsTheVoice() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        assertThat(controller.sentence.value).isEqualTo(SpokenSentence(1, "One.", index = 0, total = 2))
        engine.finishSegment()
        assertThat(controller.sentence.value).isEqualTo(SpokenSentence(1, "Two.", index = 1, total = 2))
    }

    @Test fun stopping_clearsTheSentence() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.stop()
        assertThat(controller.sentence.value).isNull()
    }

    @Test fun rates_snapToATenth_withinTheRange() {
        assertThat(SpeechRates.snap(1.26f)).isEqualTo(1.3f)
        assertThat(SpeechRates.snap(9f)).isEqualTo(SpeechRates.MAX)
        assertThat(SpeechRates.snap(0.1f)).isEqualTo(SpeechRates.MIN)
    }

    @Test fun nextSentence_movesOnStraightAway() {
        pages[1] = "One. Two. Three."
        controller.start(1, 1)
        controller.nextSentence()
        assertThat(engine.spoken.last()).isEqualTo("Two.")
        assertThat(controller.sentence.value?.index).isEqualTo(1)
    }

    @Test fun nextSentence_atTheEndOfAPage_goesToTheNextPage() {
        pages[1] = "One."
        pages[2] = "Two."
        controller.start(1, 2)
        controller.nextSentence()
        assertThat(engine.spoken.last()).isEqualTo("Two.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(2))
    }

    @Test fun nextSentence_atTheEndOfTheDocument_staysThere() {
        pages[1] = "One."
        controller.start(1, 1)
        controller.nextSentence()
        assertThat(engine.spoken).containsExactly("One.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
    }

    @Test fun previousSentence_goesBack() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.nextSentence()
        controller.previousSentence()
        assertThat(engine.spoken.last()).isEqualTo("One.")
    }

    @Test fun previousSentence_atTheStartOfAPage_goesToTheEndOfThePreviousOne() {
        pages[1] = "One. Two."
        pages[2] = "Three."
        controller.start(2, 2)
        controller.previousSentence()
        assertThat(engine.spoken.last()).isEqualTo("Two.")
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Reading(1))
    }

    @Test fun previousSentence_skipsEmptyPages_goingBack() {
        pages[1] = "One."
        pages[2] = ""
        pages[3] = "Three."
        controller.start(3, 3)
        controller.previousSentence()
        assertThat(engine.spoken.last()).isEqualTo("One.")
    }

    @Test fun previousSentence_withNothingBefore_saysTheFirstAgain() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.previousSentence()
        assertThat(engine.spoken).containsExactly("One.", "One.").inOrder()
    }

    @Test fun movingWhilePaused_staysPaused() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.pause()
        controller.nextSentence()
        assertThat(engine.spoken).containsExactly("One.")
        assertThat(controller.sentence.value?.index).isEqualTo(1)
        assertThat(controller.state.value).isEqualTo(ReadAloudState.Paused(1))
        controller.resume()
        assertThat(engine.spoken.last()).isEqualTo("Two.")
    }

    @Test fun rate_appliesAtOnce_byRepeatingTheSentence() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.setRate(2.5f)
        assertThat(engine.rates).containsExactly(2.5f)
        assertThat(engine.spoken).containsExactly("One.", "One.").inOrder()
    }

    @Test fun voice_appliesAtOnce_byRepeatingTheSentence() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        controller.setVoice("it-it-x-itc-local")
        assertThat(engine.chosenVoices).containsExactly("it-it-x-itc-local")
        assertThat(engine.spoken).containsExactly("One.", "One.").inOrder()
    }

    @Test fun rate_whilePaused_waitsForResume() {
        pages[1] = "One."
        controller.start(1, 1)
        controller.pause()
        controller.setRate(2f)
        assertThat(engine.rates).containsExactly(2f)
        assertThat(engine.spoken).containsExactly("One.")
    }

    @Test fun rate_whileAPageIsLoading_speaksNothingTwice() {
        gate = CompletableDeferred()
        pages[1] = "One."
        controller.start(1, 1)
        controller.setRate(2f)
        assertThat(engine.spoken).isEmpty()
        gate?.complete(Unit)
        assertThat(engine.spoken).containsExactly("One.")
    }

    @Test fun anOldSentenceFinishing_afterARepeat_doesNotSkipAhead() {
        pages[1] = "One. Two."
        controller.start(1, 1)
        val old = engine.lastId
        controller.setRate(2f)
        engine.onSegmentDone?.invoke(checkNotNull(old))
        assertThat(engine.spoken.last()).isEqualTo("One.")
    }

    @Test fun aRepeatedSentence_knowsWhichOneItIs() {
        pages[1] = "Again. Other. Again."
        controller.start(1, 1)
        assertThat(controller.sentence.value?.occurrence).isEqualTo(0)
        engine.finishSegment()
        assertThat(controller.sentence.value?.occurrence).isEqualTo(0)
        engine.finishSegment()
        assertThat(controller.sentence.value?.occurrence).isEqualTo(1)
    }
}
