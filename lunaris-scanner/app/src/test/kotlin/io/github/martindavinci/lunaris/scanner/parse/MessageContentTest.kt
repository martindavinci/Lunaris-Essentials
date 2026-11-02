package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Email
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Phone
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Sms
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import org.junit.Test

class MessageContentTest {
    @Test fun mailtoAddressOnly() {
        assertThat(parse("mailto:a@example.com")).isEqualTo(Email("a@example.com", null, null))
        assertThat(parse("MAILTO:a@example.com")).isEqualTo(Email("a@example.com", null, null))
    }

    @Test fun mailtoWithSubjectAndBody() {
        assertThat(parse("mailto:a@example.com?subject=Hello%20there&body=Line%0Atwo%2B1+x"))
            .isEqualTo(Email("a@example.com", "Hello there", "Line\ntwo+1+x"))
        assertThat(parse("mailto:a@example.com?BODY=b&Subject=s"))
            .isEqualTo(Email("a@example.com", "s", "b"))
    }

    @Test fun mailtoIgnoresOtherAndEmptyParameters() {
        assertThat(parse("mailto:a@example.com?cc=b@example.com&subject=&novalue"))
            .isEqualTo(Email("a@example.com", null, null))
    }

    @Test fun mailtoDecodesUtf8AndAddress() {
        assertThat(parse("mailto:a%40example.com?subject=Caff%C3%A8"))
            .isEqualTo(Email("a@example.com", "Caffè", null))
    }

    @Test fun badPercentSequencesStayLiteral() {
        assertThat(parse("mailto:a@example.com?subject=100%&body=%ZZ%4"))
            .isEqualTo(Email("a@example.com", "100%", "%ZZ%4"))
    }

    @Test fun mailtoWithoutARecipientIsText() {
        val malformed = listOf(
            "mailto:",
            "mailto:?subject=x",
            "mailto:nobody",
            "mailto:@example.com",
            "mailto:a@",
        )
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun matmsg() {
        assertThat(parse("MATMSG:TO:a@example.com;SUB:Hello;BODY:Hi there;;"))
            .isEqualTo(Email("a@example.com", "Hello", "Hi there"))
        assertThat(parse("matmsg:body:b;to:a@example.com;;"))
            .isEqualTo(Email("a@example.com", null, "b"))
        assertThat(parse("""MATMSG:TO:a@example.com;SUB:a\;b\:c;;"""))
            .isEqualTo(Email("a@example.com", "a;b:c", null))
    }

    @Test fun matmsgWithoutARecipientIsText() {
        listOf("MATMSG:", "MATMSG:SUB:x;;", "MATMSG:TO:;;", "MATMSG:TO:nobody;;").forEach {
            assertThat(parse(it)).isEqualTo(Text(it))
        }
    }

    @Test fun tel() {
        assertThat(parse("tel:+1-555-0100")).isEqualTo(Phone("+1-555-0100"))
        assertThat(parse("TEL:0123456")).isEqualTo(Phone("0123456"))
        assertThat(parse("tel:(555) 010.0")).isEqualTo(Phone("(555) 010.0"))
        assertThat(parse("tel:%2B39123")).isEqualTo(Phone("+39123"))
        assertThat(parse("tel:*21*123#")).isEqualTo(Phone("*21*123#"))
        assertThat(parse("tel:+1555;ext=12")).isEqualTo(Phone("+1555;ext=12"))
    }

    @Test fun telWithoutANumberIsText() {
        listOf("tel:", "tel:abc", "tel:+", "tel:javascript:alert(1)", "tel://123").forEach {
            assertThat(parse(it)).isEqualTo(Text(it))
        }
    }

    @Test fun smsNumberOnly() {
        assertThat(parse("sms:+1555")).isEqualTo(Sms("+1555", null))
        assertThat(parse("SMS:+1555")).isEqualTo(Sms("+1555", null))
        assertThat(parse("smsto:+1555")).isEqualTo(Sms("+1555", null))
        assertThat(parse("SMSTO:+1555")).isEqualTo(Sms("+1555", null))
    }

    @Test fun smsBodyFromTheQuery() {
        assertThat(parse("sms:+1555?body=Hello%20there")).isEqualTo(Sms("+1555", "Hello there"))
        assertThat(parse("sms:+1555?BODY=x")).isEqualTo(Sms("+1555", "x"))
        assertThat(parse("sms:+1555;?&body=x")).isEqualTo(Sms("+1555", "x"))
        assertThat(parse("sms:+1555?body=")).isEqualTo(Sms("+1555", null))
    }

    @Test fun smsBodyAfterAColonIsTakenAsWritten() {
        assertThat(parse("SMSTO:+1555:Are you 100%25 sure?"))
            .isEqualTo(Sms("+1555", "Are you 100%25 sure?"))
        assertThat(parse("sms:+1555:a:b")).isEqualTo(Sms("+1555", "a:b"))
        assertThat(parse("smsto:+1555:")).isEqualTo(Sms("+1555", null))
    }

    @Test fun smsToSeveralNumbers() {
        assertThat(parse("sms:+1555,+1666?body=x")).isEqualTo(Sms("+1555,+1666", "x"))
    }

    @Test fun smsWithoutANumberIsText() {
        listOf("sms:", "smsto:", "sms:?body=x", "smsto::hello", "sms:abc", "smsto:abc:hello").forEach {
            assertThat(parse(it)).isEqualTo(Text(it))
        }
    }
}
