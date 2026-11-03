package io.github.martindavinci.lunaris.pdf.url

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BidiControlsTest {
    @Test fun controlsThatReorderTextAreSpelledOut() {
        assertThat("a‮b".withVisibleBidiControls()).isEqualTo("a\\u202Eb")
        assertThat("‪⁦⁩".withVisibleBidiControls()).isEqualTo("\\u202A\\u2066\\u2069")
    }

    @Test fun ordinaryTextIsLeftAlone() {
        val family = "👩‍👧"
        assertThat("Ciao שלום".withVisibleBidiControls()).isEqualTo("Ciao שלום")
        assertThat(family.withVisibleBidiControls()).isEqualTo(family)
    }
}
