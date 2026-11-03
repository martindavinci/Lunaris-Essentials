package io.github.martindavinci.lunaris.pdf.annotate

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditedFileStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val store by lazy { EditedFileStore(folder.root.resolve("edited")) }
    private val one = byteArrayOf(1, 2, 3)
    private val two = byteArrayOf(4, 5)

    private fun fileOf(uri: String) = File(URI(uri))

    @Test fun pieces_inOrder_makeTheFile() = runBlocking {
        store.begin()
        assertThat(store.append(0, one)).isTrue()
        assertThat(store.append(1, two)).isTrue()
        val uri = checkNotNull(store.finish(5))
        assertThat(fileOf(uri).readBytes()).isEqualTo(one + two)
        assertThat(uri).startsWith("file:/")
    }

    @Test fun aPieceOutOfOrder_isRefused() = runBlocking {
        store.begin()
        assertThat(store.append(1, two)).isFalse()
        assertThat(store.finish(2)).isNull()
    }

    @Test fun theWrongSize_isNoFile() = runBlocking {
        store.begin()
        store.append(0, one)
        assertThat(store.finish(4)).isNull()
    }

    @Test fun beginning_clearsWhatWasLeftBefore() = runBlocking {
        store.begin()
        store.append(0, one)
        val old = fileOf(checkNotNull(store.finish(3)))
        store.begin()
        assertThat(old.length()).isEqualTo(0)
    }

    @Test fun clearing_leavesNothing() = runBlocking {
        store.begin()
        store.append(0, one)
        val made = fileOf(checkNotNull(store.finish(3)))
        store.clear()
        assertThat(made.exists()).isFalse()
        assertThat(store.append(0, one)).isFalse()
    }

    @Test fun clearing_withNothingThere_isFine() = runBlocking {
        store.clear()
        assertThat(store.finish(0)).isNull()
    }

    @Test fun nothingBegun_acceptsNothing() = runBlocking {
        assertThat(store.append(0, one)).isFalse()
        assertThat(store.finish(0)).isNull()
    }
}
