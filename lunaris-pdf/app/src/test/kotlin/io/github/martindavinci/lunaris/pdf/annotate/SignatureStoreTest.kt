package io.github.martindavinci.lunaris.pdf.annotate

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignatureStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val store by lazy { SignatureStore(folder.root.resolve("signatures")) }
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2)

    @Test fun nothingSaved_listsNothing() = runBlocking {
        assertThat(store.list()).isEmpty()
    }

    @Test fun saved_isListedAndReadBack() = runBlocking {
        store.add(png)
        val saved = store.list().single()
        assertThat(store.read(saved.id)).isEqualTo(png)
        assertThat(saved.path).endsWith(".png")
    }

    @Test fun whatIsNotAPng_isNotSaved() = runBlocking {
        store.add(byteArrayOf(1, 2, 3, 4))
        assertThat(store.list()).isEmpty()
    }

    @Test fun removed_isGone() = runBlocking {
        store.add(png)
        store.add(png)
        val first = store.list().first()
        store.remove(first.id)
        assertThat(store.list().map { it.id }).doesNotContain(first.id)
        assertThat(store.list()).hasSize(1)
    }

    @Test fun anIdThatLeavesTheFolder_isRefused() = runBlocking {
        store.add(png)
        assertThat(store.read("../../secret")).isNull()
    }
}
