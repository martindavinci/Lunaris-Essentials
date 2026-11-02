package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Contact
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import org.junit.Test

class MeCardContentTest {
    @Test fun allFields() {
        val text = "MECARD:N:Doe,Jane;ORG:Acme;TITLE:CTO;TEL:+1555;TEL:+1666;EMAIL:j@example.com;" +
            "ADR:1 Main St;URL:https://example.com;NOTE:Hi;;"
        assertThat(parse(text)).isEqualTo(
            Contact(
                name = "Jane Doe",
                organization = "Acme",
                title = "CTO",
                phones = listOf("+1555", "+1666"),
                emails = listOf("j@example.com"),
                address = "1 Main St",
                website = "https://example.com",
                note = "Hi",
            ),
        )
    }

    @Test fun fieldsInAnyOrderAndAnyCase() {
        val contact = parse("mecard:tel:1;n:Jane;;") as Contact
        assertThat(contact.name).isEqualTo("Jane")
        assertThat(contact.phones).containsExactly("1")
    }

    @Test fun escapes() {
        val contact = parse("""MECARD:N:Doe\, Jr,Jane;NOTE:a\;b\:c\\d\,e;;""") as Contact
        assertThat(contact.name).isEqualTo("Jane Doe, Jr")
        assertThat(contact.note).isEqualTo("""a;b:c\d,e""")
    }

    @Test fun withNothingUsefulItIsText() {
        listOf("MECARD:", "MECARD:;;", "MECARD:N:;TEL:;;", "MECARD:SOUND:x;;").forEach {
            assertThat(parse(it)).isEqualTo(Text(it))
        }
    }
}
