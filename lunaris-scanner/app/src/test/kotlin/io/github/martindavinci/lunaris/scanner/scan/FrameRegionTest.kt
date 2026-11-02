package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameRegionTest {
    private val frame = FrameRegion(left = 0, top = 0, width = 1920, height = 1080)

    @Test fun wholeWindow_coversTheVisibleRegion() {
        assertThat(frame.behindWindow(90, 1f, 1f)).isEqualTo(frame)
    }

    @Test fun uprightFrame_usesTheSharesAsTheyAre() {
        val region = frame.behindWindow(0, widthShare = 0.5f, heightShare = 0.25f)

        assertThat(region.width).isEqualTo((1920 * 0.56f).toInt())
        assertThat(region.height).isEqualTo((1080 * 0.31f).toInt())
    }

    @Test fun sidewaysFrame_swapsTheShares() {
        val region = frame.behindWindow(90, widthShare = 0.5f, heightShare = 0.25f)

        assertThat(region.width).isEqualTo((1920 * 0.31f).toInt())
        assertThat(region.height).isEqualTo((1080 * 0.56f).toInt())
    }

    @Test fun region_isCentredInTheVisiblePart() {
        val visible = FrameRegion(left = 100, top = 50, width = 1000, height = 500)

        val region = visible.behindWindow(0, widthShare = 0.44f, heightShare = 0.44f)

        assertThat(region).isEqualTo(FrameRegion(350, 175, 500, 250))
    }
}
