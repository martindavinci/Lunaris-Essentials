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
        override fun setRate(rate: Float) = Unit
        override fun setVoice(voiceId: String?) = Unit
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
}
