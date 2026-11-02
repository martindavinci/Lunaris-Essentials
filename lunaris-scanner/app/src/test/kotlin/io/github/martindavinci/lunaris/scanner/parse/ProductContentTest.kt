package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Product
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Url
import org.junit.Test

class ProductContentTest {
    @Test fun retailFormatsWithDigitsAreProducts() {
        val samples = mapOf(
            CodeFormat.EAN_13 to "4006381333931",
            CodeFormat.EAN_8 to "73513537",
            CodeFormat.UPC_A to "036000291452",
            CodeFormat.UPC_E to "01234565",
        )
        samples.forEach { (format, digits) ->
            assertThat(parse(digits, format)).isEqualTo(Product(digits, format))
        }
    }

    @Test fun otherFormatsWithDigitsAreText() {
        val others = CodeFormat.entries - listOf(
            CodeFormat.EAN_13,
            CodeFormat.EAN_8,
            CodeFormat.UPC_A,
            CodeFormat.UPC_E,
        )
        others.forEach { format ->
            assertThat(parse("4006381333931", format)).isEqualTo(Text("4006381333931"))
        }
    }

    @Test fun retailFormatsWithoutPlainDigitsAreNotProducts() {
        assertThat(parse("123A", CodeFormat.EAN_13)).isEqualTo(Text("123A"))
        assertThat(parse("", CodeFormat.EAN_13)).isEqualTo(Text(""))
        assertThat(parse("١٢٣", CodeFormat.EAN_8)).isEqualTo(Text("١٢٣"))
    }

    @Test fun otherLinearFormatsCanCarryAnyContent() {
        assertThat(parse("https://example.com", CodeFormat.CODE_128))
            .isEqualTo(Url("https://example.com"))
    }
}
