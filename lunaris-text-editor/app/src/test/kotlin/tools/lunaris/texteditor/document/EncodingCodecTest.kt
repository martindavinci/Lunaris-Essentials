package tools.lunaris.texteditor.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.StandardCharsets

class EncodingCodecTest {

    private val codec = EncodingCodec()

    @Test
    fun decode_utf8_roundTrips_textAndCharset() {
        val bytes = "hello world".toByteArray(StandardCharsets.UTF_8)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("hello world")
        assertThat(decoded.charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(decoded.lineEnding).isEqualTo(LineEnding.LF)
    }

    @Test
    fun decode_stripsUtf8Bom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "abc".toByteArray(StandardCharsets.UTF_8)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("abc")
    }

    @Test
    fun decode_detectsCrlf_andNormalizesToLf() {
        val bytes = "a\r\nb".toByteArray(StandardCharsets.UTF_8)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("a\nb")
        assertThat(decoded.lineEnding).isEqualTo(LineEnding.CRLF)
    }

    @Test
    fun encode_crlf_writesCarriageReturns() {
        val bytes = codec.encode("a\nb", StandardCharsets.UTF_8, LineEnding.CRLF)
        assertThat(String(bytes, StandardCharsets.UTF_8)).isEqualTo("a\r\nb")
    }

    @Test
    fun encode_utf8_lf_roundTripsWithoutBom() {
        val original = "x\ny"
        val bytes = codec.encode(original, StandardCharsets.UTF_8, LineEnding.LF)
        assertThat(bytes[0]).isNotEqualTo(0xEF.toByte())
        assertThat(String(bytes, StandardCharsets.UTF_8)).isEqualTo(original)
    }

    @Test
    fun decode_forcedCharset_isUsed() {
        val bytes = "café".toByteArray(StandardCharsets.ISO_8859_1)
        val decoded = codec.decode(bytes, forced = StandardCharsets.ISO_8859_1)
        assertThat(decoded.text).isEqualTo("café")
        assertThat(decoded.charset).isEqualTo(StandardCharsets.ISO_8859_1)
    }

    @Test
    fun decode_detectsUtf16LeBom() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + "hi".toByteArray(StandardCharsets.UTF_16LE)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("hi")
        assertThat(decoded.charset).isEqualTo(StandardCharsets.UTF_16LE)
    }

    @Test
    fun decode_detectsUtf16BeBom() {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + "hi".toByteArray(StandardCharsets.UTF_16BE)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("hi")
        assertThat(decoded.charset).isEqualTo(StandardCharsets.UTF_16BE)
    }

    @Test
    fun decode_invalidUtf8_fallsBackToLatin1_lossless() {
        // 0xE9 is "é" in ISO-8859-1 but an invalid standalone UTF-8 byte.
        val bytes = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte()) // "café" in Latin-1
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.charset).isEqualTo(StandardCharsets.ISO_8859_1)
        assertThat(decoded.text).isEqualTo("café")
        assertThat(decoded.hasBom).isFalse()
    }

    @Test
    fun decode_validUtf8_staysUtf8_noBom() {
        val decoded = codec.decode("ciào".toByteArray(StandardCharsets.UTF_8), forced = null)
        assertThat(decoded.charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(decoded.text).isEqualTo("ciào")
        assertThat(decoded.hasBom).isFalse()
    }

    @Test
    fun decode_utf8Bom_setsHasBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val decoded = codec.decode(bom + "abc".toByteArray(StandardCharsets.UTF_8), forced = null)
        assertThat(decoded.hasBom).isTrue()
    }

    @Test
    fun encode_withBom_prependsUtf8Bom_andRoundTrips() {
        val bytes = codec.encode("abc", StandardCharsets.UTF_8, LineEnding.LF, hasBom = true)
        assertThat(bytes.copyOfRange(0, 3)).isEqualTo(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        // Decoding the result strips the BOM back out and flags it.
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("abc")
        assertThat(decoded.hasBom).isTrue()
    }

    @Test
    fun encode_withBom_utf16le_prependsBom() {
        val bytes = codec.encode("hi", StandardCharsets.UTF_16LE, LineEnding.LF, hasBom = true)
        assertThat(bytes.copyOfRange(0, 2)).isEqualTo(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
    }

    @Test
    fun encode_withoutBom_hasNoBom() {
        val bytes = codec.encode("abc", StandardCharsets.UTF_8, LineEnding.LF, hasBom = false)
        assertThat(bytes[0]).isNotEqualTo(0xEF.toByte())
    }

    @Test
    fun decode_detectsCr_andNormalizesToLf() {
        val bytes = "a\rb".toByteArray(StandardCharsets.UTF_8)
        val decoded = codec.decode(bytes, forced = null)
        assertThat(decoded.text).isEqualTo("a\nb")
        assertThat(decoded.lineEnding).isEqualTo(LineEnding.CR)
    }

    @Test
    fun canEncode_isFalse_forCharsOutsideCharset() {
        assertThat(codec.canEncode("café", StandardCharsets.ISO_8859_1)).isTrue()
        assertThat(codec.canEncode("€", StandardCharsets.ISO_8859_1)).isFalse()
    }

    @Test(expected = java.nio.charset.CharacterCodingException::class)
    fun encode_refusesToSilentlyReplaceUnmappableChars() {
        codec.encode("price: €", StandardCharsets.ISO_8859_1, LineEnding.LF)
    }
}
