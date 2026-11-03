package io.github.martindavinci.lunaris.pdf.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageJumpTest {
    @Test fun aLongMove_isAJump() {
        assertThat(PageJump.after(null, from = 12, to = 20)).isEqualTo(PageJump(returnTo = 12, landedOn = 20))
    }

    @Test fun aShortMove_keepsWhatWasThere() {
        val jump = PageJump(returnTo = 3, landedOn = 20)
        assertThat(PageJump.after(jump, from = 20, to = 22)).isEqualTo(jump)
        assertThat(PageJump.after(null, from = 20, to = 18)).isNull()
    }

    @Test fun reading_nearTheLanding_keepsTheJump() {
        val jump = PageJump(returnTo = 12, landedOn = 20)
        assertThat(jump.whileReading(22)).isEqualTo(jump)
        assertThat(jump.whileReading(18)).isEqualTo(jump)
    }

    @Test fun reading_awayFromTheLanding_endsTheJump() {
        val jump = PageJump(returnTo = 12, landedOn = 20)
        assertThat(jump.whileReading(23)).isNull()
        assertThat(jump.whileReading(17)).isNull()
    }

    @Test fun goingBack_offersTheWayForward() {
        val back = PageJump(returnTo = 12, landedOn = 20).reversed()
        assertThat(back).isEqualTo(PageJump(returnTo = 20, landedOn = 12, forward = true))
        assertThat(back.reversed()).isEqualTo(PageJump(returnTo = 12, landedOn = 20, forward = false))
    }
}
