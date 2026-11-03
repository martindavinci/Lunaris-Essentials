package io.github.martindavinci.lunaris.pdf.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechChunkerTest {

    @Test fun shortText_singleChunk() {
        assertThat(SpeechChunker.chunk("hello world", 4000)).containsExactly("hello world")
    }

    @Test fun longText_splitsUnderLimit_andIsLossless() {
        val text = "word ".repeat(2000) // 10000 chars
        val chunks = SpeechChunker.chunk(text, 100)
        assertThat(chunks.size).isAtLeast(2)
        assertThat(chunks.all { it.length <= 100 }).isTrue()
        assertThat(chunks.joinToString("")).isEqualTo(text) // reconstructs original exactly
    }

    @Test fun breaksAtWhitespace_notMidWord() {
        val text = "alpha ".repeat(10) // 60 chars, spaces every 6
        val chunks = SpeechChunker.chunk(text, 14)
        // Each chunk should end on a word boundary (space), never mid-"alpha".
        assertThat(chunks.dropLast(1).all { it.endsWith(" ") }).isTrue()
        assertThat(chunks.joinToString("")).isEqualTo(text)
    }

    @Test fun unbrokenRun_hardCutAtLimit() {
        val chunks = SpeechChunker.chunk("x".repeat(50), 10)
        assertThat(chunks).hasSize(5)
        assertThat(chunks.all { it.length == 10 }).isTrue()
    }

    @Test fun exactlyLimit_singleChunk() {
        val text = "a".repeat(100)
        assertThat(SpeechChunker.chunk(text, 100)).containsExactly(text)
    }
}
