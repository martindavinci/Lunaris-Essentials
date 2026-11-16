package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import org.junit.Test

class EngineErrorsTest {
    private fun c(msg: String?, errno: Int = 0, kind: ArchiveKind = ArchiveKind.ZIP) = EngineErrors.classify(kind, errno, msg)

    @Test fun table() {
        assertThat(c("Unrecognized archive format")).isEqualTo(EngineStatus.NOT_ARCHIVE)
        assertThat(c("Encryption is not supported", kind = ArchiveKind.RAR5)).isEqualTo(EngineStatus.HEADERS_ENCRYPTED)
        assertThat(c("The header is encrypted, but currently not supported", kind = ArchiveKind.SEVEN_Z)).isEqualTo(EngineStatus.HEADERS_ENCRYPTED)
        assertThat(c("The file content is encrypted, but currently not supported", kind = ArchiveKind.SEVEN_Z)).isEqualTo(EngineStatus.DATA_ENCRYPTED)
        assertThat(c("Reading encrypted data is not currently supported")).isEqualTo(EngineStatus.DATA_ENCRYPTED)
        assertThat(c("Decryption is unsupported due to lack of crypto library")).isEqualTo(EngineStatus.DATA_ENCRYPTED)
        assertThat(c("Encrypted file is unsupported")).isEqualTo(EngineStatus.DATA_ENCRYPTED)
        assertThat(c("RAR encryption support unavailable", kind = ArchiveKind.RAR4)).isEqualTo(EngineStatus.RAR4_ENCRYPTED)
        assertThat(c("Declared dictionary size is not supported")).isEqualTo(EngineStatus.DICTIONARY_TOO_LARGE)
        assertThat(c("Truncated RAR5 archive: file continues in a subsequent volume")).isEqualTo(EngineStatus.NEEDS_NEXT_VOLUME)
        assertThat(c("Truncated input file (needed 512 bytes, only 3 available)")).isEqualTo(EngineStatus.TRUNCATED)
        assertThat(c(null, errno = 12)).isEqualTo(EngineStatus.OUT_OF_MEMORY)
        assertThat(c("Can't read", errno = 13)).isEqualTo(EngineStatus.ACCESS_DENIED)
        assertThat(c("anything else")).isEqualTo(EngineStatus.DAMAGED)
    }
    @Test fun encryptedHeaders_onlyForFormatsThatHaveThem() {
        assertThat(c("Encryption is not supported", kind = ArchiveKind.TAR)).isEqualTo(EngineStatus.DAMAGED)
    }
}
