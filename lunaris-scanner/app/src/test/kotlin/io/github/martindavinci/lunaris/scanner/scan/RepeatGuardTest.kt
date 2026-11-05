package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test

class RepeatGuardTest {
    private val guard = RepeatGuard(quietMillis = 1_000L)
    private val code = ScannedCode("a", CodeFormat.QR_CODE)
    private val other = ScannedCode("b", CodeFormat.QR_CODE)

    @Test fun firstSighting_passes() {
        assertThat(guard.accept(code, nowMillis = 0L)).isTrue()
    }

    @Test fun sameCodeRightAway_isHeldBack() {
        guard.accept(code, nowMillis = 0L)

        assertThat(guard.accept(code, nowMillis = 999L)).isFalse()
    }

    @Test fun differentCode_passes() {
        guard.accept(code, nowMillis = 0L)

        assertThat(guard.accept(other, nowMillis = 1L)).isTrue()
    }

    @Test fun twoCodesInViewTogether_eachPassOnlyOnce() {
        assertThat(guard.accept(code, nowMillis = 0L)).isTrue()
        assertThat(guard.accept(other, nowMillis = 100L)).isTrue()

        assertThat(guard.accept(code, nowMillis = 200L)).isFalse()
        assertThat(guard.accept(other, nowMillis = 300L)).isFalse()
    }

    @Test fun rearming_holdsBackEveryCodeLastSeen() {
        guard.accept(code, nowMillis = 0L)
        guard.accept(other, nowMillis = 0L)

        guard.rearm(nowMillis = 100_000L)

        assertThat(guard.accept(code, nowMillis = 100_001L)).isFalse()
        assertThat(guard.accept(other, nowMillis = 100_002L)).isFalse()
    }

    @Test fun aForgottenCode_passesAgainAtOnce() {
        guard.accept(code, nowMillis = 0L)

        guard.forget(code)

        assertThat(guard.accept(code, nowMillis = 1L)).isTrue()
    }

    @Test fun forgettingOneCode_leavesTheOthersHeldBack() {
        guard.accept(code, nowMillis = 0L)
        guard.accept(other, nowMillis = 0L)

        guard.forget(code)

        assertThat(guard.accept(other, nowMillis = 1L)).isFalse()
    }

    @Test fun sameCodeAfterTheQuietTime_passes() {
        guard.accept(code, nowMillis = 0L)

        assertThat(guard.accept(code, nowMillis = 1_000L)).isTrue()
    }

    @Test fun codeKeptInView_isHeldBackForAsLongAsItStays() {
        guard.accept(code, nowMillis = 0L)

        assertThat(guard.accept(code, nowMillis = 900L)).isFalse()
        assertThat(guard.accept(code, nowMillis = 1_800L)).isFalse()
        assertThat(guard.accept(code, nowMillis = 2_700L)).isFalse()
    }

    @Test fun comingBackAfterALongTime_stillHoldsBackTheSameCode() {
        guard.accept(code, nowMillis = 0L)

        guard.rearm(nowMillis = 60_000L)

        assertThat(guard.accept(code, nowMillis = 60_100L)).isFalse()
        assertThat(guard.accept(other, nowMillis = 60_200L)).isTrue()
    }
}
