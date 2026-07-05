package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_000_000_000_000L
    private fun ago(ms: Long) = RelativeTime.of(now - ms, now)

    @Test fun zeroOrNegative_isNull() {
        assertThat(RelativeTime.of(0L, now)).isNull()
        assertThat(RelativeTime.of(-5L, now)).isNull()
    }
    @Test fun underMinute_isNow() { assertThat(ago(30_000)).isEqualTo(RelativeLabel.Now) }
    @Test fun minutes() { assertThat(ago(5 * 60_000)).isEqualTo(RelativeLabel.Minutes(5)) }
    @Test fun hours() { assertThat(ago(2 * 3_600_000)).isEqualTo(RelativeLabel.Hours(2)) }
    @Test fun yesterday() { assertThat(ago(26 * 3_600_000)).isEqualTo(RelativeLabel.Yesterday) }
    @Test fun days() { assertThat(ago(3L * 24 * 3_600_000)).isEqualTo(RelativeLabel.Days(3)) }
    @Test fun old_isOlderWithMillis() {
        val then = now - 40L * 24 * 3_600_000
        assertThat(RelativeTime.of(then, now)).isEqualTo(RelativeLabel.Older(then))
    }
}
