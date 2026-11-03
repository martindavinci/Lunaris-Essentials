package io.github.martindavinci.lunaris.pdf.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SegmentProgressTest {
    private val finished = mutableListOf<String>()
    private val progress = SegmentProgress { finished += it }

    @Test fun segmentIsDone_whenItsLastChunkFinishes() {
        val ids = progress.begin("7", chunks = 3)
        assertThat(ids).containsExactly("7#0", "7#1", "7#2").inOrder()
        progress.chunkFinished("7#0")
        progress.chunkFinished("7#1")
        assertThat(finished).isEmpty()
        progress.chunkFinished("7#2")
        assertThat(finished).containsExactly("7")
    }

    @Test fun lastChunkEndingInAnError_stillEndsTheSegment() {
        progress.begin("7", chunks = 1)
        progress.chunkFailed("7#0")
        assertThat(finished).containsExactly("7")
    }

    @Test fun earlierChunkEndingInAnError_doesNotEndTheSegment() {
        progress.begin("7", chunks = 2)
        progress.chunkFailed("7#0")
        assertThat(finished).isEmpty()
    }

    @Test fun afterStopping_nothingIsReported() {
        progress.begin("7", chunks = 1)
        progress.clear()
        progress.chunkFinished("7#0")
        assertThat(finished).isEmpty()
    }

    @Test fun chunkOfAnOlderSegment_isIgnored() {
        progress.begin("7", chunks = 1)
        progress.begin("8", chunks = 1)
        progress.chunkFinished("7#0")
        assertThat(finished).isEmpty()
    }
}
