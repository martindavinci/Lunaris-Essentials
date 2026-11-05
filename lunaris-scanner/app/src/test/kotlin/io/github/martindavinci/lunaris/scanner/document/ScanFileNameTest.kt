package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ScanFileNameTest {
    @Test fun theNameCarriesDateAndTime() {
        val name = scanFileName("pdf", LocalDateTime.of(2026, 11, 5, 11, 53, 9))

        assertThat(name).isEqualTo("lunaris-scan-20261105-115309.pdf")
    }

    @Test fun earlyHoursKeepTheirZeros() {
        val name = scanFileName("png", LocalDateTime.of(2027, 1, 2, 3, 4, 5))

        assertThat(name).isEqualTo("lunaris-scan-20270102-030405.png")
    }

    @Test fun theDigitsStayLatinInAnyLanguage() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        try {
            val name = scanFileName("jpg", LocalDateTime.of(2026, 11, 5, 11, 53, 9))

            assertThat(name).isEqualTo("lunaris-scan-20261105-115309.jpg")
        } finally {
            Locale.setDefault(before)
        }
    }
}
