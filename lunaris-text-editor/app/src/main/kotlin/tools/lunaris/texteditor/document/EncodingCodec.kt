package tools.lunaris.texteditor.document

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/** Result of decoding raw bytes into editor text (normalized to LF internally). */
data class DecodedText(
    val text: String,
    val charset: Charset,
    val lineEnding: LineEnding,
    /** True if the source began with a byte-order mark, so saving can re-emit it. */
    val hasBom: Boolean = false,
)

/**
 * Converts between raw file bytes and editor text.
 * Internally the editor always holds LF-only text; the original line ending is
 * remembered so saving can reproduce it.
 */
class EncodingCodec @Inject constructor() {

    /**
     * Decodes raw [bytes] into editor text normalized to LF.
     *
     * @param forced when non-null, this charset is used directly and a leading BOM is
     *   NOT stripped — the caller must ensure [bytes] has no BOM. When null, the charset
     *   is auto-detected: a BOM (UTF-8 / UTF-16) wins and is stripped; otherwise the bytes
     *   are decoded as strict UTF-8, falling back to ISO-8859-1 if they aren't valid UTF-8
     *   (so legacy single-byte files round-trip instead of gaining U+FFFD replacements).
     */
    fun decode(bytes: ByteArray, forced: Charset?): DecodedText {
        val detected: Triple<Charset, Int, Boolean>? = when {
            forced != null -> Triple(forced, 0, false)
            hasUtf8Bom(bytes) -> Triple(StandardCharsets.UTF_8, 3, true)
            hasUtf16LeBom(bytes) -> Triple(StandardCharsets.UTF_16LE, 2, true)
            hasUtf16BeBom(bytes) -> Triple(StandardCharsets.UTF_16BE, 2, true)
            else -> null
        }
        val (charset, contentStart, hasBom) = detected ?: autoDetectNoBom(bytes)
        val raw = String(bytes, contentStart, bytes.size - contentStart, charset)
        val lineEnding = LineEnding.detect(raw)
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        return DecodedText(normalized, charset, lineEnding, hasBom)
    }

    fun encode(text: String, charset: Charset, lineEnding: LineEnding, hasBom: Boolean = false): ByteArray {
        val withEndings = when (lineEnding) {
            LineEnding.LF -> text
            LineEnding.CRLF -> text.replace("\n", "\r\n")
            LineEnding.CR -> text.replace("\n", "\r")
        }
        val body = withEndings.toByteArray(charset)
        val bom = if (hasBom) bomFor(charset) else null
        return if (bom != null) bom + body else body
    }

    /** No BOM and no forced charset: prefer strict UTF-8, fall back to Latin-1 (lossless). */
    private fun autoDetectNoBom(bytes: ByteArray): Triple<Charset, Int, Boolean> {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            Triple(StandardCharsets.UTF_8, 0, false)
        } catch (_: CharacterCodingException) {
            Triple(StandardCharsets.ISO_8859_1, 0, false)
        }
    }

    private fun bomFor(charset: Charset): ByteArray? = when (charset) {
        StandardCharsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        StandardCharsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        StandardCharsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> null
    }

    private fun hasUtf8Bom(b: ByteArray) =
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()

    private fun hasUtf16LeBom(b: ByteArray) =
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()

    private fun hasUtf16BeBom(b: ByteArray) =
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()
}
