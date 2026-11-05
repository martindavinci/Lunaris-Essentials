package io.github.martindavinci.lunaris.scanner.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class ScanPdfWriterTest {
    private val first = ByteArray(300) { (it % 251).toByte() }
    private val second = ByteArray(77) { (200 - it).toByte() }

    private fun twoPages(): ByteArray {
        val out = ByteArrayOutputStream()
        ScanPdfWriter(out).apply {
            addJpegPage(first, PageSize(10, 20), PagePoints(100f, 200f))
            addJpegPage(second, PageSize(30, 40), PagePoints(595.28f, 841.89f))
            finish()
        }
        return out.toByteArray()
    }

    private fun ByteArray.text() = String(this, Charsets.ISO_8859_1)

    private fun tableOf(text: String): String {
        val start = Regex("startxref\n(\\d+)\n").find(text)!!.groupValues[1].toInt()
        return text.substring(start)
    }

    @Test fun startsAndEndsLikeAPdf() {
        val text = twoPages().text()

        assertThat(text).startsWith("%PDF-1.4\n")
        assertThat(text).endsWith("%%EOF\n")
    }

    @Test fun listsEveryPage() {
        val text = twoPages().text()

        assertThat(text).contains("/Type /Pages")
        assertThat(text).contains("/Count 2")
        assertThat(Regex("/Type /Page\\b").findAll(text).count()).isEqualTo(2)
    }

    @Test fun eachPageHasItsOwnSizes() {
        val text = twoPages().text()

        assertThat(text).contains("/MediaBox [0 0 100.00 200.00]")
        assertThat(text).contains("/MediaBox [0 0 595.28 841.89]")
        assertThat(text).contains("/Width 10 /Height 20")
        assertThat(text).contains("/Width 30 /Height 40")
        assertThat(text).contains("q 595.28 0 0 841.89 0 0 cm /Im0 Do Q")
    }

    @Test fun holdsTheJpegBytesUntouched_withTheirExactLength() {
        val text = twoPages().text()

        assertThat(text).contains("/Length 300 >>\nstream\n${first.text()}\nendstream")
        assertThat(text).contains("/Length 77 >>\nstream\n${second.text()}\nendstream")
    }

    @Test fun theTablePointsAtEveryObject() {
        val text = twoPages().text()
        val table = tableOf(text)
        assertThat(table).startsWith("xref\n0 9\n")

        val offsets = Regex("(\\d{10}) 00000 n ").findAll(table).map { it.groupValues[1].toInt() }.toList()
        assertThat(offsets).hasSize(8)
        offsets.forEachIndexed { index, offset ->
            assertThat(text.substring(offset)).startsWith("${index + 1} 0 obj\n")
        }
    }

    @Test fun eachTableLineIsTwentyBytes() {
        val lines = tableOf(twoPages().text()).substringAfter("xref\n0 9\n").substringBefore("trailer").split("\n").dropLast(1)

        assertThat(lines).hasSize(9)
        lines.forEach { assertThat(it.length + 1).isEqualTo(20) }
    }

    @Test fun saysNothingOfWhoOrWhen() {
        val text = twoPages().text()

        listOf("/Info", "/Author", "/Producer", "/Creator", "/CreationDate", "/ModDate", "/Metadata").forEach {
            assertThat(text).doesNotContain(it)
        }
    }

    @Test fun aSinglePage_isAValidFileToo() {
        val out = ByteArrayOutputStream()
        ScanPdfWriter(out).apply {
            addJpegPage(first, PageSize(10, 20), PagePoints(100f, 200f))
            finish()
        }

        assertThat(out.toByteArray().text()).contains("/Count 1")
    }
}
