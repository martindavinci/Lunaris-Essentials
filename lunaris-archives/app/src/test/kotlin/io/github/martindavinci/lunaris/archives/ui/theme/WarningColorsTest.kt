package io.github.martindavinci.lunaris.archives.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WarningColorsTest {
    @Test fun amber_hasReadableContrast_inBothThemes() {
        assertThat(contrastRatio(WarningColors.dark.container, WarningColors.dark.content)).isAtLeast(MIN_CONTRAST)
        assertThat(contrastRatio(WarningColors.light.container, WarningColors.light.content)).isAtLeast(MIN_CONTRAST)
    }
    @Test fun amber_isNotRed() {
        assertThat(hueOf(WarningColors.dark.container)).isIn(AMBER)
        assertThat(hueOf(WarningColors.light.container)).isIn(AMBER)
    }
    @Test fun contrast_ofBlackOnWhite_is21() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
    }

    private fun hueOf(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue); val min = minOf(c.red, c.green, c.blue); val d = max - min
        val h = when (max) { c.red -> ((c.green - c.blue) / d) % 6; c.green -> (c.blue - c.red) / d + 2; else -> (c.red - c.green) / d + 4 } * 60
        return if (h < 0) h + 360 else h
    }

    private companion object {
        const val MIN_CONTRAST = 4.5
        val AMBER = com.google.common.collect.Range.closed(30f, 50f)
    }
}
