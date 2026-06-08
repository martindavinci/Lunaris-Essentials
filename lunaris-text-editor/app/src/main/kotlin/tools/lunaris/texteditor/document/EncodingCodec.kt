package tools.lunaris.texteditor.document

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Result of decoding raw bytes into editor text (normalized to LF internally). */
data class DecodedText(
    val text: String,
    val charset: Charset,
    val lineEnding: LineEnding,
)

/**
 * Converts between raw file bytes and editor text.
 * Internally the editor always holds LF-only text; the original line ending is
 * remembered so saving can reproduce it.
 */
class EncodingCodec {

    fun decode(bytes: ByteArray, forced: Charset?): DecodedText {
        val (charset, contentStart) = when {
            forced != null -> forced to 0
            hasUtf8Bom(bytes) -> StandardCharsets.UTF_8 to 3
            hasUtf16LeBom(bytes) -> StandardCharsets.UTF_16LE to 2
            hasUtf16BeBom(bytes) -> StandardCharsets.UTF_16BE to 2
            else -> StandardCharsets.UTF_8 to 0
        }
        val raw = String(bytes, contentStart, bytes.size - contentStart, charset)
        val lineEnding = LineEnding.detect(raw)
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        return DecodedText(normalized, charset, lineEnding)
    }

    fun encode(text: String, charset: Charset, lineEnding: LineEnding): ByteArray {
        val withEndings = when (lineEnding) {
            LineEnding.LF -> text
            LineEnding.CRLF -> text.replace("\n", "\r\n")
            LineEnding.CR -> text.replace("\n", "\r")
        }
        return withEndings.toByteArray(charset)
    }

    private fun hasUtf8Bom(b: ByteArray) =
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()

    private fun hasUtf16LeBom(b: ByteArray) =
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()

    private fun hasUtf16BeBom(b: ByteArray) =
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()
}
