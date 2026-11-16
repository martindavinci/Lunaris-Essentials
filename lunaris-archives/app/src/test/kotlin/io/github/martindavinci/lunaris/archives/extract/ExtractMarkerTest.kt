package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

class ExtractMarkerTest {
    private val store = object : KeyValue {
        var value: String? = null
        override fun get() = value
        override fun put(value: String) { this.value = value }
        override fun remove() { value = null }
    }

    @Test fun aSetTokenSurvivesANewInstance() {
        PrefsExtractMarker(store).set("doc:1")
        assertThat(PrefsExtractMarker(store).pending()).isEqualTo("doc:1")
    }
    @Test fun clear_forgetsIt() {
        val marker = PrefsExtractMarker(store)
        marker.set("doc:1"); marker.clear()
        assertThat(marker.pending()).isNull()
    }
    @Test fun recover_deletesThePendingDocumentOnce() {
        PrefsExtractMarker(store).set("doc:1")
        val deleted = mutableListOf<String>()
        val marker = PrefsExtractMarker(store)
        assertThat(marker.recover { deleted += it; true }).isTrue()
        assertThat(deleted).containsExactly("doc:1")
        assertThat(marker.recover { deleted += it; true }).isFalse()
        assertThat(marker.takeRecovered()).isTrue()
        assertThat(marker.takeRecovered()).isFalse()
    }
    @Test fun noSpace_isFoundInTheMessageOrTheCauses() {
        assertThat(FreeSpace.isNoSpace(IOException("write failed: ENOSPC (No space left on device)"))).isTrue()
        assertThat(FreeSpace.isNoSpace(RuntimeException(IOException("ENOSPC")))).isTrue()
        assertThat(FreeSpace.isNoSpace(IOException("EIO"))).isFalse()
    }
}
