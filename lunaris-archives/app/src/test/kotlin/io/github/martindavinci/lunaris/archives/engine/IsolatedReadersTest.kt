package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IsolatedReadersTest {
    @Test fun routes() {
        assertThat(IsolatedReaders.of(EngineRoute.ZIP4J)).isInstanceOf(ZipCryptoReader::class.java)
        assertThat(IsolatedReaders.of(EngineRoute.SEVEN_Z)).isInstanceOf(SevenZipReader::class.java)
        assertThat(IsolatedReaders.of(EngineRoute.LIB_RAR)).isInstanceOf(RarReader5::class.java)
    }
}
