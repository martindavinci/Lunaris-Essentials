package io.github.martindavinci.lunaris.pdf.feature.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwipeRemovalTest {
    private fun removes(offset: Float, velocity: Float = 0f) =
        SwipeRemoval.removes(offset, velocity, width = 1000f, flingSpeed = 2000f)

    @Test fun aShortSlide_goesBack() {
        assertThat(removes(100f)).isFalse()
        assertThat(removes(-100f)).isFalse()
    }

    @Test fun pastTheLine_removes_eitherWay() {
        assertThat(removes(500f)).isTrue()
        assertThat(removes(-500f)).isTrue()
    }

    @Test fun aQuickFlick_removes_eitherWay() {
        assertThat(removes(150f, velocity = 3000f)).isTrue()
        assertThat(removes(-150f, velocity = -3000f)).isTrue()
    }

    @Test fun aFlickBackTowardsItsPlace_keepsIt() {
        assertThat(removes(500f, velocity = -3000f)).isFalse()
        assertThat(removes(-500f, velocity = 3000f)).isFalse()
    }

    @Test fun aRowThatHasNotMoved_stays() {
        assertThat(removes(0f, velocity = 3000f)).isFalse()
    }

    @Test fun aRowNotYetMeasured_stays() {
        assertThat(SwipeRemoval.removes(100f, 0f, width = 0f, flingSpeed = 2000f)).isFalse()
    }
}
