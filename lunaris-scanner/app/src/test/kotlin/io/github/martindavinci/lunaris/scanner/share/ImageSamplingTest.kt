package io.github.martindavinci.lunaris.scanner.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageSamplingTest {
    @Test fun smallImage_isNotSampled() {
        assertThat(sampleSizeFor(width = 800, height = 600, maxSide = 2048)).isEqualTo(1)
    }

    @Test fun sideAtTheLimit_isNotSampled() {
        assertThat(sampleSizeFor(width = 2048, height = 2048, maxSide = 2048)).isEqualTo(1)
    }

    @Test fun longerSideDecides() {
        assertThat(sampleSizeFor(width = 100, height = 2049, maxSide = 2048)).isEqualTo(2)
        assertThat(sampleSizeFor(width = 9000, height = 100, maxSide = 2048)).isEqualTo(8)
    }

    @Test fun unreadableBounds_areNotSampled() {
        assertThat(sampleSizeFor(width = -1, height = -1, maxSide = 2048)).isEqualTo(1)
    }
}
