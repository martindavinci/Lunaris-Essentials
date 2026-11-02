package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test

class ReadAgreementTest {
    private val agreement = ReadAgreement(windowMillis = 100)
    private val ean = ScannedCode("4006381333931", CodeFormat.EAN_13)
    private val halfOfIt = ScannedCode("01234565", CodeFormat.UPC_E)

    @Test fun firstReadOfAWeakFormat_waits() {
        assertThat(agreement.accept(ean, 0)).isFalse()
    }

    @Test fun sameReadAgain_passes() {
        agreement.accept(ean, 0)

        assertThat(agreement.accept(ean, 50)).isTrue()
    }

    @Test fun anotherReadInBetween_startsOver() {
        agreement.accept(ean, 0)
        agreement.accept(halfOfIt, 10)

        assertThat(agreement.accept(ean, 20)).isFalse()
    }

    @Test fun readOutsideTheWindow_doesNotAgree() {
        agreement.accept(ean, 0)

        assertThat(agreement.accept(ean, 101)).isFalse()
    }

    @Test fun reset_forgetsTheCandidate() {
        agreement.accept(ean, 0)
        agreement.reset()

        assertThat(agreement.accept(ean, 10)).isFalse()
    }

    @Test fun afterPassing_anotherAgreementIsNeeded() {
        agreement.accept(ean, 0)
        agreement.accept(ean, 10)

        assertThat(agreement.accept(ean, 20)).isFalse()
    }

    @Test fun formatsWithAStrongCheck_passAtOnce() {
        assertThat(agreement.accept(ScannedCode("x", CodeFormat.QR_CODE), 0)).isTrue()
        assertThat(agreement.accept(ScannedCode("x", CodeFormat.CODE_128), 0)).isTrue()
    }
}
