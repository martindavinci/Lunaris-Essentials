package io.github.martindavinci.lunaris.archives.archive

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import io.github.martindavinci.lunaris.archives.testing.SevenZipBytes
import org.junit.Test
import java.io.File
import java.nio.channels.FileChannel

class SevenZipKdfCheckTest {
    private fun power(f: File) = FileChannel.open(f.toPath()).use { SevenZipKdfCheck.cyclesPower(it) }
    private fun headersEncrypted(f: File) = FileChannel.open(f.toPath()).use { SevenZipKdfCheck.headersEncrypted(it) }

    @Test fun aesCoder_reportsItsCyclesPower() { assertThat(power(SevenZipBytes.withAes(19))).isEqualTo(19) }
    @Test fun aboveTheLimit_isReportedToo() { assertThat(power(SevenZipBytes.withAes(30))).isEqualTo(30) }
    @Test fun aesOnTheHeader_meansEncryptedHeaders() { assertThat(headersEncrypted(SevenZipBytes.withAes(19))).isTrue() }
    @Test fun noSevenZip_isNull() {
        assertThat(power(Fixtures.file("plain.zip"))).isNull()
        assertThat(headersEncrypted(Fixtures.file("plain.zip"))).isFalse()
    }
    @Test fun brokenHeader_isNull() {
        assertThat(power(SevenZipBytes.withAes(19, headerBytes = byteArrayOf(0x17, 0x06)))).isNull()
        assertThat(power(SevenZipBytes.withAes(19, headerBytes = byteArrayOf(0x42)))).isNull()
    }
    @Test fun headerPastTheEnd_isNull() {
        val f = SevenZipBytes.withAes(19)
        f.writeBytes(f.readBytes().copyOf(40))
        assertThat(power(f)).isNull()
    }
    @Test fun aesInsideACompressedHeader_isFound() {
        assertThat(power(Fixtures.file("data-enc.7z"))).isEqualTo(19)
        assertThat(headersEncrypted(Fixtures.file("data-enc.7z"))).isFalse()
    }
    @Test fun encryptedHeaders_ofARealArchive() {
        assertThat(headersEncrypted(Fixtures.file("head-enc.7z"))).isTrue()
    }
}
