package io.github.martindavinci.lunaris.pdf.url

private const val HEX_RADIX = 16
private const val ESCAPE_PREFIX = "\\u"
private val BIDI_CONTROLS = listOf('‪'..'‮', '⁦'..'⁩')

/**
 * The text of an untrusted code as it is safe to show: the characters that reorder what comes after
 * them are spelled out, so that a code cannot make its text read as something else. Other invisible
 * characters stay, as languages and emoji need them.
 */
fun String.withVisibleBidiControls(): String = buildString(length) {
    this@withVisibleBidiControls.forEach { character ->
        if (BIDI_CONTROLS.any { character in it }) {
            append(ESCAPE_PREFIX).append(character.code.toString(HEX_RADIX).uppercase())
        } else {
            append(character)
        }
    }
}
