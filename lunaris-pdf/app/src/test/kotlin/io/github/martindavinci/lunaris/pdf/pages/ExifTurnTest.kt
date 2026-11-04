package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExifTurnTest {
    @Test
    fun `all eight orientations, and anything else stands as it is`() {
        val expected = mapOf(
            1 to ExifTurn(0, false),
            2 to ExifTurn(0, true),
            3 to ExifTurn(180, false),
            4 to ExifTurn(180, true),
            5 to ExifTurn(270, true),
            6 to ExifTurn(90, false),
            7 to ExifTurn(90, true),
            8 to ExifTurn(270, false),
            0 to ExifTurn(0, false),
            99 to ExifTurn(0, false),
        )
        expected.forEach { (orientation, turn) -> assertThat(ExifTurn.of(orientation)).isEqualTo(turn) }
    }

    @Test
    fun `the mark of encryption is found anywhere, and only that`() {
        assertThat(PdfProtection.mentions("trailer << /Encrypt 5 0 R >>".toByteArray())).isTrue()
        assertThat(PdfProtection.mentions("trailer << /Root 5 0 R >>".toByteArray())).isFalse()
        assertThat(PdfProtection.mentions(ByteArray(0))).isFalse()
    }
}
