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
}
