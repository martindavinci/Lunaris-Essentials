package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Contact
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import org.junit.Test

class VCardContentTest {
    private fun vcard(vararg lines: String) =
        (listOf("BEGIN:VCARD") + lines + "END:VCARD").joinToString("\r\n")

    private fun contact(text: String) = parse(text) as Contact

    @Test fun fullVCard30() {
        val text = vcard(
            "VERSION:3.0",
            "N:Doe;Jane;;;",
            "FN:Jane Doe",
            "ORG:Acme Inc.",
            "TITLE:Engineer",
            "TEL;TYPE=WORK,VOICE:+1-555-0100",
            "TEL;TYPE=CELL:+1-555-0101",
            "EMAIL;TYPE=INTERNET:jane@example.com",
            "EMAIL:doe@example.com",
            "ADR;TYPE=WORK:;;123 Main St;Springfield;IL;62704;USA",
            "URL:https://example.com",
            "NOTE:Hello",
        )
        assertThat(parse(text)).isEqualTo(
            Contact(
                name = "Jane Doe",
                organization = "Acme Inc.",
                title = "Engineer",
                phones = listOf("+1-555-0100", "+1-555-0101"),
                emails = listOf("jane@example.com", "doe@example.com"),
                address = "123 Main St, Springfield, IL, 62704, USA",
                website = "https://example.com",
                note = "Hello",
            ),
        )
    }

    @Test fun lineEndingsAndCaseDoNotMatter() {
        val text = "begin:vcard\nversion:3.0\nfn:Jane\ntel:123\nend:vcard"
        assertThat(contact(text).name).isEqualTo("Jane")
        assertThat(contact(text).phones).containsExactly("123")
        assertThat(contact("BEGIN:VCARD\rFN:Jane\rEND:VCARD").name).isEqualTo("Jane")
    }

    @Test fun vCard21StyleParameters() {
        val text = vcard("VERSION:2.1", "N:Doe;Jane", "TEL;CELL;VOICE:+1555", "EMAIL;INTERNET:a@b.example")
        assertThat(contact(text).name).isEqualTo("Jane Doe")
        assertThat(contact(text).phones).containsExactly("+1555")
        assertThat(contact(text).emails).containsExactly("a@b.example")
    }

    @Test fun vCard21QuotedPrintable() {
        val text = vcard(
            "VERSION:2.1",
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Caff=C3=A8 =",
            "Bar",
            "NOTE;QUOTED-PRINTABLE:one=0Atwo",
            "ORG;ENCODING=QUOTED-PRINTABLE;CHARSET=ISO-8859-1:Caff=E8",
            "TITLE;ENCODING=QUOTED-PRINTABLE;CHARSET=no-such-charset:a=3Db",
        )
        assertThat(contact(text).name).isEqualTo("Caffè Bar")
        assertThat(contact(text).note).isEqualTo("one\ntwo")
        assertThat(contact(text).organization).isEqualTo("Caffè")
        assertThat(contact(text).title).isEqualTo("a=b")
    }

    @Test fun charsetsThatCannotEncodeAreSafe() {
        val text = vcard("FN;ENCODING=QUOTED-PRINTABLE;CHARSET=ISO-2022-CN:a=3Db", "NOTE:x")
        assertThat(contact(text).note).isEqualTo("x")
    }

    @Test fun vCard40Forms() {
        val text = vcard(
            "VERSION:4.0",
            "FN:Jane",
            "TEL;VALUE=uri;TYPE=\"voice,cell\":tel:+1-555-0100",
            "TEL;VALUE=text;PREF=1:555 0101",
            "TEL;VALUE=uri:TEL:+1-555-0102",
            "EMAIL;PREF=1:jane@example.com",
            "ADR;LABEL=\"Main: St; 1\":;;1 Main St;;;;",
            "item1.URL:https://example.com",
        )
        assertThat(contact(text).phones).containsExactly("+1-555-0100", "555 0101", "+1-555-0102").inOrder()
        assertThat(contact(text).emails).containsExactly("jane@example.com")
        assertThat(contact(text).address).isEqualTo("1 Main St")
        assertThat(contact(text).website).isEqualTo("https://example.com")
    }

    @Test fun foldedLines() {
        val text = "BEGIN:VCARD\r\nFN:Jane\r\n  Doe\r\nNOTE:one\r\n\t two\r\nTEL;\r\n TYPE=CELL:1\r\nEND:VCARD"
        assertThat(contact(text).name).isEqualTo("Jane Doe")
        assertThat(contact(text).note).isEqualTo("one two")
        assertThat(contact(text).phones).containsExactly("1")
    }

    @Test fun escapesInValues() {
        val text = vcard(
            """FN:Doe\, Jane""",
            """NOTE:line one\nline two\Nthree\; a\:b \\ c\d""",
            """ORG:Acme\; Sons;Sales""",
            """ADR:;;1\, Main St\nFloor 2;Town;;;""",
            """URL:https\://example.com""",
        )
        assertThat(contact(text).name).isEqualTo("Doe, Jane")
        assertThat(contact(text).note).isEqualTo("line one\nline two\nthree; a:b \\ c\\d")
        assertThat(contact(text).organization).isEqualTo("Acme; Sons, Sales")
        assertThat(contact(text).address).isEqualTo("1, Main St\nFloor 2, Town")
        assertThat(contact(text).website).isEqualTo("https://example.com")
    }

    @Test fun formattedNameWinsThenStructuredName() {
        assertThat(contact(vcard("N:Doe;Jane", "FN:J. D.")).name).isEqualTo("J. D.")
        assertThat(contact(vcard("FN:", "N:Doe;Jane")).name).isEqualTo("Jane Doe")
        assertThat(contact(vcard("N:Doe;Jane;Q.;Dr.;Jr.")).name).isEqualTo("Dr. Jane Q. Doe Jr.")
        assertThat(contact(vcard("N:Doe")).name).isEqualTo("Doe")
    }

    @Test fun firstValueWinsForSingleFields() {
        val text = vcard("FN:One", "FN:Two", "ORG:A", "ORG:B", "NOTE:", "NOTE:Real")
        assertThat(contact(text).name).isEqualTo("One")
        assertThat(contact(text).organization).isEqualTo("A")
        assertThat(contact(text).note).isEqualTo("Real")
    }

    @Test fun blankPhonesAndEmailsAreDropped() {
        val text = vcard("FN:Jane", "TEL:", "TEL;TYPE=CELL: 1 ", "EMAIL:  ")
        assertThat(contact(text).phones).containsExactly("1")
        assertThat(contact(text).emails).isEmpty()
    }

    @Test fun missingEndIsTolerated() {
        assertThat(contact("BEGIN:VCARD\nFN:Jane").name).isEqualTo("Jane")
    }

    @Test fun whatFollowsEndIsIgnored() {
        assertThat(contact("BEGIN:VCARD\nFN:Jane\nEND:VCARD\nTEL:1").phones).isEmpty()
    }

    @Test fun linesWithoutAColonAreIgnored() {
        val text = vcard("garbage", "FN:Jane", "TEL;TYPE=\"unterminated:1")
        assertThat(contact(text).name).isEqualTo("Jane")
        assertThat(contact(text).phones).isEmpty()
    }

    @Test fun vCardWithNothingUsefulIsText() {
        val malformed = listOf(
            "BEGIN:VCARD",
            "BEGIN:VCARD\nEND:VCARD",
            "BEGIN:VCARD\nVERSION:3.0\nEND:VCARD",
            "BEGIN:VCARD\nFN:\nTEL:\nEND:VCARD",
            "BEGIN:VCARDS\nFN:Jane\nEND:VCARD",
        )
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }
}
