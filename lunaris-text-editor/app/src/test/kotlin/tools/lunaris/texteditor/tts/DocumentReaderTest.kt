package tools.lunaris.texteditor.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private class FakeSpeechEngine : SpeechEngine {
    val spoken = mutableListOf<Pair<String, String>>() // text to id
    // Backed by a private field + read-only getter (not `var rate`): a `var` here would emit a
    // JVM setRate(float)/setVoice(String) that clashes with the override below (platform
    // declaration clash) since both compile to the same erased signature.
    private var rateValue = 1f
    val rate: Float get() = rateValue
    private var voiceValue: String? = null
    val voice: String? get() = voiceValue
    var stopCount = 0
    override var onSegmentDone: ((String) -> Unit)? = null
    override var onUnavailable: (() -> Unit)? = null
    override fun speak(text: String, utteranceId: String) { spoken.add(text to utteranceId) }
    override fun stop() { stopCount++ }
    override fun setRate(rate: Float) { rateValue = rate }
    override fun setVoice(voiceId: String?) { voiceValue = voiceId }
    override fun voices(): List<VoiceInfo> = emptyList()
    override fun shutdown() {}
    fun complete(id: String) { onSegmentDone?.invoke(id) }
}

class DocumentReaderTest {
    private val engine = FakeSpeechEngine()
    private val reader = DocumentReader(engine)

    @Test fun start_speaksFirstSegment_andReports() {
        reader.start(listOf("a", "b", "c"), rate = 1f, voiceId = null)
        assertThat(engine.spoken).containsExactly("a" to "0")
        assertThat(reader.state.value.reading).isTrue()
        assertThat(reader.state.value.total).isEqualTo(3)
    }

    @Test fun advancesOnDone_untilFinished() {
        reader.start(listOf("a", "b"), 1f, null)
        engine.complete("0")
        assertThat(engine.spoken.last()).isEqualTo("b" to "1")
        assertThat(reader.state.value.index).isEqualTo(1)
        engine.complete("1")
        assertThat(reader.state.value.reading).isFalse()
    }

    @Test fun next_clampsAtLast_prev_clampsAtZero() {
        reader.start(listOf("a", "b"), 1f, null)
        reader.next(); reader.next()
        assertThat(reader.state.value.index).isEqualTo(1)
        reader.previous(); reader.previous()
        assertThat(reader.state.value.index).isEqualTo(0)
    }

    @Test fun pause_stops_thenResume_reSpeaksCurrent() {
        reader.start(listOf("a", "b"), 1f, null)
        reader.togglePause()
        assertThat(engine.stopCount).isEqualTo(1)
        assertThat(reader.state.value.paused).isTrue()
        val before = engine.spoken.size
        reader.togglePause()
        assertThat(reader.state.value.paused).isFalse()
        assertThat(engine.spoken.size).isEqualTo(before + 1)
        assertThat(engine.spoken.last()).isEqualTo("a" to "0")
    }

    @Test fun setRate_appliesAndReSpeaks() {
        reader.start(listOf("a"), 1f, null)
        reader.setRate(2.5f)
        assertThat(engine.rate).isEqualTo(2.5f)
        assertThat(reader.state.value.rate).isEqualTo(2.5f)
        assertThat(engine.spoken.last()).isEqualTo("a" to "0")
    }

    @Test fun stop_endsReading() {
        reader.start(listOf("a"), 1f, null)
        reader.stop()
        assertThat(reader.state.value.reading).isFalse()
        assertThat(engine.stopCount).isAtLeast(1)
    }

    @Test fun setVoice_appliesAndReSpeaks() {
        reader.start(listOf("a"), 1f, null)
        val before = engine.spoken.size
        reader.setVoice("it-it-x-itc-local")
        assertThat(engine.voice).isEqualTo("it-it-x-itc-local")
        assertThat(reader.state.value.voiceId).isEqualTo("it-it-x-itc-local")
        assertThat(engine.spoken.size).isEqualTo(before + 1)
        assertThat(engine.spoken.last()).isEqualTo("a" to "0")
    }

    @Test fun staleDoneCallback_isIgnored() {
        reader.start(listOf("a", "b", "c"), 1f, null)
        reader.next()                       // index -> 1, speaks ("b","1")
        val sizeAfterNext = engine.spoken.size
        engine.complete("0")                // stale id from the flushed segment 0
        assertThat(reader.state.value.index).isEqualTo(1)     // did not advance
        assertThat(engine.spoken.size).isEqualTo(sizeAfterNext) // did not re-speak
    }
}
