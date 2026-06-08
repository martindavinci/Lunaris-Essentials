package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorLiteralTest {
    @Test fun formatsHexUpper() {
        assertThat(ColorLiteral.hex(0xFF0000)).isEqualTo("#FF0000")
        assertThat(ColorLiteral.hex(0x00FF00)).isEqualTo("#00FF00")
        assertThat(ColorLiteral.hex(0x000000)).isEqualTo("#000000")
    }
    @Test fun masksToLow24Bits() {
        assertThat(ColorLiteral.hex(-0x1)).isEqualTo("#FFFFFF") // 0xFFFFFFFF -> low 24 bits
    }
}
