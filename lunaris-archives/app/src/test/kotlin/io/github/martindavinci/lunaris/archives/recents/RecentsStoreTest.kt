package io.github.martindavinci.lunaris.archives.recents

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecentsStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { RecentsStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "r.preferences_pb") }) }
    private fun item(n: Int) = RecentArchive("content://a/$n", "a$n.zip", "ZIP", 1, 1, false, n.toLong(), null)

    @After fun stop() = scope.cancel()

    @Test fun newestFirst_andTheOldestFallsOffTheEnd() = runBlocking<Unit> {
        (1..Limits.RECENTS_MAX).forEach { store.remember(item(it)) }
        val evicted = store.remember(item(Limits.RECENTS_MAX + 1))
        assertThat(evicted.map { it.uri }).containsExactly("content://a/1")
        assertThat(store.recents.first().first().uri).isEqualTo("content://a/${Limits.RECENTS_MAX + 1}")
    }
    @Test fun removeAndRestore_keepTheTimeOrder() = runBlocking<Unit> {
        (1..3).forEach { store.remember(item(it)) }
        val removed = store.remove("content://a/2")!!
        assertThat(store.recents.first().map { it.uri }).containsExactly("content://a/3", "content://a/1").inOrder()
        store.restore(removed)
        assertThat(store.recents.first().map { it.uri }).containsExactly("content://a/3", "content://a/2", "content://a/1").inOrder()
    }
    @Test fun clearReturnsEverything_andUpdatePathKeepsTheRest() = runBlocking<Unit> {
        store.remember(item(1)); store.updatePath("content://a/1", listOf("docs"))
        assertThat(store.recents.first().single().lastPath).containsExactly("docs")
        assertThat(store.clear()).hasSize(1)
        assertThat(store.recents.first()).isEmpty()
    }
}
