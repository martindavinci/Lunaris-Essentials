package io.github.martindavinci.lunaris.scanner.feature.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_000_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun ago(millis: Long) = RelativeTime.of(now - millis, now)

    @Test fun zeroOrNegative_isUnknown() {
        assertThat(RelativeTime.of(0L, now)).isNull()
        assertThat(RelativeTime.of(-5L, now)).isNull()
    }

    @Test fun underAMinute_isNow() {
        assertThat(ago(30_000)).isEqualTo(RelativeLabel.Now)
    }

    @Test fun inTheFuture_isNow() {
        assertThat(ago(-hour)).isEqualTo(RelativeLabel.Now)
    }

    @Test fun minutes() {
        assertThat(ago(5 * minute)).isEqualTo(RelativeLabel.Minutes(5))
    }

    @Test fun hours() {
        assertThat(ago(2 * hour)).isEqualTo(RelativeLabel.Hours(2))
    }

    @Test fun yesterday() {
        assertThat(ago(26 * hour)).isEqualTo(RelativeLabel.Yesterday)
    }

    @Test fun days() {
        assertThat(ago(3 * day)).isEqualTo(RelativeLabel.Days(3))
    }

    @Test fun aMonthOrMore_isAnAbsoluteDate() {
        val then = now - 40 * day

        assertThat(RelativeTime.of(then, now)).isEqualTo(RelativeLabel.Older(then))
    }
}
