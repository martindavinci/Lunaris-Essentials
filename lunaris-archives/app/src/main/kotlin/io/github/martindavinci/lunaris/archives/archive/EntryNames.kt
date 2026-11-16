package io.github.martindavinci.lunaris.archives.archive

import io.github.martindavinci.lunaris.archives.text.withVisibleBidiControls
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Spec: valid UTF-8 → UTF-8; else CP437 for ZIP names without the EFS flag; else replacement.
 *
 * [decode] keeps characters that reorder text: spelled out, they would contain a backslash, which
 * the path rules read as a folder separator. [display] is what the screens show.
 */
object EntryNames {
    private const val ASCII_END = 0x80
    internal const val CP437_HIGH =
        "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00A0"

    /** The name as text, for the path rules and for writing. */
    fun decode(raw: ByteArray, utf8Flag: Boolean, zipWithoutEfs: Boolean): String {
        val strict = strictUtf8(raw)
        return when {
            strict != null -> strict
            utf8Flag || !zipWithoutEfs -> String(raw, Charsets.UTF_8)   // U+FFFD where it is not UTF-8
            else -> cp437(raw)
        }
    }

    /** The name as it is safe to show: characters that reorder the text are spelled out. */
    fun display(name: String): String = name.withVisibleBidiControls()

    private fun strictUtf8(raw: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun cp437(raw: ByteArray): String = buildString(raw.size) {
        raw.forEach { b ->
            val v = b.toInt() and BYTE_MASK
            append(if (v < ASCII_END) v.toChar() else CP437_HIGH[v - ASCII_END])
        }
    }

    private const val BYTE_MASK = 0xFF
}
