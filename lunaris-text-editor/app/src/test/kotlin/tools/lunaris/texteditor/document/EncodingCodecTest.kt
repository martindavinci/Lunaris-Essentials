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
}
