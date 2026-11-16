package io.github.martindavinci.lunaris.archives.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RawPathTest {
    @Test fun equalBytes_areEqual() {
        assertThat(RawPath.of(byteArrayOf(1, 2))).isEqualTo(RawPath.of(byteArrayOf(1, 2)))
        assertThat(RawPath.of(byteArrayOf(1, 2)).hashCode()).isEqualTo(RawPath.of(byteArrayOf(1, 2)).hashCode())
        assertThat(RawPath.of(byteArrayOf(1, 2)).bytes).isEqualTo(byteArrayOf(1, 2))
    }
    @Test fun theBytesAreCopies() {
        val source = byteArrayOf(1, 2)
        val path = RawPath.of(source)
        source[0] = 9
        path.bytes[1] = 9
        assertThat(path.bytes).isEqualTo(byteArrayOf(1, 2))
    }
    @Test fun head_keepsTheFirstBytes() {
        val path = RawPath.of(byteArrayOf(1, 2, 3))
        assertThat(path.size).isEqualTo(3)
        assertThat(path.head(2)).isEqualTo(RawPath.of(byteArrayOf(1, 2)))
        assertThat(path.head(5)).isSameInstanceAs(path)
    }
    @Test fun codesRoundTrip() {
        EntryType.entries.forEach { assertThat(EntryType.of(it.code)).isEqualTo(it) }
        Encryption.entries.forEach { assertThat(Encryption.of(it.code)).isEqualTo(it) }
        HostOs.entries.forEach { assertThat(HostOs.of(it.code)).isEqualTo(it) }
    }
    @Test fun unknownCodes_throw() {
        assertThrows(NoSuchElementException::class.java) { EntryType.of(99) }
        assertThrows(NoSuchElementException::class.java) { Encryption.of(99) }
        assertThrows(NoSuchElementException::class.java) { HostOs.of(99) }
    }
    @Test fun sameEntry_toleratesHowEachReaderSpellsTheName() {
        fun p(text: String) = RawPath.of(text.toByteArray(Charsets.UTF_8))
        val nfc = "Résumé.pdf"
        val nfd = "Résumé.pdf"
        assertThat(p(nfd).sameEntryAs(p(nfc))).isTrue()
        assertThat(p("docs\\a.txt").sameEntryAs(p("docs/a.txt"))).isTrue()
        assertThat(p("a.txt\u0000tail").sameEntryAs(p("a.txt"))).isTrue()
        assertThat(p("a.txt").sameEntryAs(p("b.txt"))).isFalse()
        assertThat(p("a.txt").sameEntryAs(null)).isFalse()
    }
    @Test fun sameEntry_ofBytesThatAreNotUtf8_needsTheSameBytes() {
        val cp437 = RawPath.of(byteArrayOf(0x82.toByte(), 0x41))
        assertThat(cp437.sameEntryAs(RawPath.of(byteArrayOf(0x82.toByte(), 0x41)))).isTrue()
        assertThat(cp437.sameEntryAs(RawPath.of(byteArrayOf(0x83.toByte(), 0x41)))).isFalse()
    }
}
