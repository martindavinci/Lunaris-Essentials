package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import java.util.Random
import org.junit.Test

class ParserRobustnessTest {
    private val seeds = listOf(
        "https://user:pw@example.com:8080/a?b=c#d",
        "https://[::1]:8443/",
        "example.com/path",
        "WIFI:T:WPA;S:a\\;b;P:c\\\\;H:true;;",
        "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;Jane\r\nFN;CHARSET=x;QUOTED-PRINTABLE:a=C3=\r\n =A8\r\n" +
            "TEL;TYPE=\"a,b\":tel:1\r\nADR:;;a\\;b;c\r\nEND:VCARD",
        "MECARD:N:Doe,Jane;TEL:1;EMAIL:a@b;;",
        "mailto:a@b.example?subject=%41&body=%C3%A8",
        "MATMSG:TO:a@b.example;SUB:x;BODY:y;;",
        "tel:+1-555;ext=1",
        "sms:+1555?body=%41",
        "SMSTO:+1555:hello",
        "geo:1.5,-2.5,3;u=1?q=a+b%20c",
        "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:a\nDTSTART;TZID=x:20261225T100000\n" +
            "DTEND:20261225T100000Z\nBEGIN:VALARM\nEND:VALARM\nEND:VEVENT\nEND:VCALENDAR",
    )
    private val crashers = listOf(
        "BEGIN:VEVENT\nDTSTART:+9999999990101T000000Z",
        "geo:\u0661,\u0662",
        "geo:\u0661.\u0662,\u0663,\u0664",
    )
    private val nasty = "\\;:,=%?&@#/[]\"'.\r\n\t \u0000\u00e8\u0430\ud83d\ude00\ud83d0zZ-+x"

    private fun parseEverywhere(text: String) {
        CodeFormat.entries.forEach { parse(text, it) }
        UrlSafety.warnings(text)
        UrlSafety.isOpenable(text)
    }

    @Test fun codesThatOnceCrashedAreSafe() {
        crashers.forEach(::parseEverywhere)
    }

    @Test fun everyTruncationIsSafe() {
        seeds.forEach { seed ->
            seed.indices.forEach { end -> parseEverywhere(seed.substring(0, end)) }
            seed.indices.forEach { start -> parseEverywhere(seed.substring(start)) }
        }
    }

    @Test fun randomMutationsAreSafe() {
        val random = Random(RANDOM_SEED)
        repeat(MUTATIONS) {
            val text = StringBuilder(seeds[random.nextInt(seeds.size)])
            repeat(1 + random.nextInt(MAX_EDITS)) {
                val position = random.nextInt(text.length + 1)
                val character = nasty[random.nextInt(nasty.length)]
                if (random.nextBoolean() && position < text.length) {
                    text.setCharAt(position, character)
                } else {
                    text.insert(position, character)
                }
            }
            parseEverywhere(text.toString())
        }
    }

    @Test fun veryLongInputsAreSafe() {
        val pieces = listOf("a.", "\\", ";", ":", "%", "=", "@", "\n", "\n ", "BEGIN:VALARM\n", "\"", "1,", "[")
        val prefixes = listOf(
            "",
            "https://",
            "WIFI:",
            "MECARD:",
            "MATMSG:",
            "mailto:",
            "tel:",
            "sms:",
            "geo:",
            "BEGIN:VCARD\n",
            "BEGIN:VEVENT\n",
        )
        prefixes.forEach { prefix ->
            pieces.forEach { piece -> parseEverywhere(prefix + piece.repeat(LONG_REPEAT)) }
        }
    }

    private companion object {
        const val RANDOM_SEED = 42L
        const val MUTATIONS = 20_000
        const val MAX_EDITS = 6
        const val LONG_REPEAT = 8_000
    }
}
