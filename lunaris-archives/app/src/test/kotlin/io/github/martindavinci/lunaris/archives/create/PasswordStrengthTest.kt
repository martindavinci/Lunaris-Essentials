package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordStrengthTest {
    private fun level(s: String) = PasswordStrength.level(s.toCharArray())

    @Test fun scale() {
        assertThat(level("")).isEqualTo(0)
        assertThat(level("abc")).isEqualTo(1)
        assertThat(level("abcdefgh")).isEqualTo(1)
        assertThat(level("abcdefgh12")).isEqualTo(2)
        assertThat(level("Abcdefgh12!x")).isEqualTo(3)
        assertThat(level("Correct horse battery staple 7")).isEqualTo(4)
    }
}
