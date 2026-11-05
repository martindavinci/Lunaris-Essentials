package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CleanSuggestionTest {
    private val width = 400
    private val height = 560
    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF202024.toInt()
    private val blue = 0xFF2A3AB0.toInt()

    private fun page(paint: (x: Int, y: Int) -> Int?) = PagePixels(
        IntArray(width * height) { index -> paint(index % width, index / width) ?: white },
        width,
        height,
    )

    private fun isText(x: Int, y: Int) = x in 40..360 && y in 40..520 && y % 14 < 3 && x % 7 != 0

    @Test fun plainText_goesBlackAndWhite() {
        assertThat(suggestedClean(page { x, y -> if (isText(x, y)) ink else null })).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun anEmptySheet_goesBlackAndWhite() {
        assertThat(suggestedClean(page { _, _ -> null })).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun aBlueSignature_keepsTheColour() {
        val signed = page { x, y ->
            when {
                x in 60..180 && y in 480..500 && (x + y) % 6 < 3 -> blue
                isText(x, y) && y < 460 -> ink
                else -> null
            }
        }

        assertThat(suggestedClean(signed)).isEqualTo(CleanMode.COLOUR)
    }

    @Test fun aSmallRoundStamp_keepsTheColour() {
        val stamped = page { x, y ->
            val dx = x - 300
            val dy = y - 480
            val distance = dx * dx + dy * dy
            when {
                distance in 500..900 -> blue
                isText(x, y) && y < 440 -> ink
                else -> null
            }
        }

        assertThat(suggestedClean(stamped)).isEqualTo(CleanMode.COLOUR)
    }

    @Test fun aPhotographOnThePage_keepsTheColour() {
        val photographed = page { x, y ->
            val level = 60 + (x + y) % 120
            if (x in 50..350 && y in 60..360) (0xFF shl 24) or (level shl 16) or (level shl 8) or level else null
        }

        assertThat(suggestedClean(photographed)).isEqualTo(CleanMode.COLOUR)
    }

    @Test fun faintColourAroundTheLetters_isNotColour() {
        // A phone lens leaves a faint tint at the edge of each stroke.
        val fringed = page { x, y ->
            when {
                isText(x, y) -> ink
                isText(x, y - 1) -> 0xFFC8D0E0.toInt()
                else -> null
            }
        }

        assertThat(suggestedClean(fringed)).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun aSliverOfTableAlongTheEdge_isNotColour() {
        // The outline never fits to the pixel: a bit of what lies around the page comes along.
        val withRim = page { x, y ->
            when {
                x < 8 || y >= height - 8 -> 0xFF8A5A2B.toInt()
                isText(x, y) -> ink
                else -> null
            }
        }

        assertThat(suggestedClean(withRim)).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }

    @Test fun aFewStrayColouredSpecks_areNotColour() {
        val specked = page { x, y ->
            when {
                x % 97 == 0 && y % 89 == 0 -> blue
                isText(x, y) -> ink
                else -> null
            }
        }

        assertThat(suggestedClean(specked)).isEqualTo(CleanMode.BLACK_AND_WHITE)
    }
}
