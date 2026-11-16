package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class LibarchiveStatusTest {
    private val sink = NativeSink(WireWriter(ByteArrayOutputStream()), expected = null)
    private fun resolve(status: EngineStatus) = LibarchiveStatus.resolve(status.code, sink)

    @Test fun ok_staysOk() { assertThat(resolve(EngineStatus.OK)).isEqualTo(EngineStatus.OK) }
    @Test fun damagedWithAMessage_isClassified() {
        sink.onError(0, "Unrecognized archive format".toByteArray())
        assertThat(resolve(EngineStatus.DAMAGED)).isEqualTo(EngineStatus.NOT_ARCHIVE)
    }
    @Test fun damagedWithoutAnError_staysDamaged() { assertThat(resolve(EngineStatus.DAMAGED)).isEqualTo(EngineStatus.DAMAGED) }
    @Test fun otherCodes_passThrough() { assertThat(resolve(EngineStatus.LIMIT_REACHED)).isEqualTo(EngineStatus.LIMIT_REACHED) }
    @Test fun theFormatDecidesWhatEncryptionMeans() {
        sink.onArchive(RAR5, intArrayOf(0), 0)
        sink.onError(0, "Encryption is not supported".toByteArray())
        assertThat(resolve(EngineStatus.DAMAGED)).isEqualTo(EngineStatus.HEADERS_ENCRYPTED)
    }

    private companion object { const val RAR5 = 0x100000 }
}
