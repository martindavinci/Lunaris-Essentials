package io.github.martindavinci.lunaris.scanner.session

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test

class CurrentResultTest {
    @Test fun startsEmpty_thenHoldsWhatWasSet() {
        val result = CurrentResult()
        assertThat(result.code.value).isNull()

        val code = ScannedCode("hello", CodeFormat.QR_CODE)
        result.set(code)
        assertThat(result.code.value).isEqualTo(code)

        result.set(null)
        assertThat(result.code.value).isNull()
    }
}
