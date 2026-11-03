package io.github.martindavinci.lunaris.pdf.recents

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThumbnailStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2)
    private val store by lazy { ThumbnailStore(folder.root.resolve("thumbs")) }

    @Test fun saved_isFoundAgain() = runBlocking {
        assertThat(store.has("content://a/1")).isFalse()
        store.save("content://a/1", jpeg)
        assertThat(store.has("content://a/1")).isTrue()
        assertThat(store.fileOf("content://a/1").readBytes()).isEqualTo(jpeg)
    }

    @Test fun whatIsNotAJpeg_isNotSaved() = runBlocking {
        store.save("content://a/1", byteArrayOf(1, 2, 3))
        assertThat(store.has("content://a/1")).isFalse()
    }

    @Test fun theFileName_sayNothingOfTheDocument() = runBlocking {
        store.save("content://secret/name.pdf", jpeg)
        assertThat(store.fileOf("content://secret/name.pdf").name).doesNotContain("secret")
    }

    @Test fun remove_deletesOnlyThatOne() = runBlocking {
        store.save("content://a/1", jpeg)
        store.save("content://a/2", jpeg)
        store.remove("content://a/1")
        assertThat(store.has("content://a/1")).isFalse()
        assertThat(store.has("content://a/2")).isTrue()
    }

    @Test fun retain_deletesWhatIsNotListed() = runBlocking {
        store.save("content://a/1", jpeg)
        store.save("content://a/2", jpeg)
        store.retain(listOf("content://a/2"))
        assertThat(store.has("content://a/1")).isFalse()
        assertThat(store.has("content://a/2")).isTrue()
    }

    @Test fun clear_deletesEverything() = runBlocking {
        store.save("content://a/1", jpeg)
        store.clear()
        assertThat(store.has("content://a/1")).isFalse()
    }
}
