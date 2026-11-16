package io.github.martindavinci.lunaris.archives.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.browse.SortField
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.recents.RecentsStore
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
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

class SettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "s.preferences_pb") } }
    private val recents by lazy { RecentsStore(dataStore) }
    private val released = mutableListOf<String>()
    private val permissions = object : UriPermissions {
        override fun takeRead(uri: String) = true
        override fun release(uri: String) { released += uri }
    }
    private val store by lazy { SettingsStore(dataStore, recents, permissions) }

    @After fun stop() = scope.cancel()

    @Test fun defaults() = runBlocking<Unit> {
        assertThat(store.settings.first()).isEqualTo(AppSettings(ThemeMode.SYSTEM, rememberRecents = true, sort = SortOrder()))
    }
    @Test fun valuesAreStored() = runBlocking<Unit> {
        store.setTheme(ThemeMode.DARK)
        store.saveSort(SortOrder(SortField.SIZE, ascending = false, foldersFirst = true))
        val s = store.settings.first()
        assertThat(s.theme).isEqualTo(ThemeMode.DARK)
        assertThat(s.sort).isEqualTo(SortOrder(SortField.SIZE, ascending = false, foldersFirst = true))
    }
    @Test fun turningRecentsOff_forgetsThemAndTheirPermissions() = runBlocking<Unit> {
        recents.remember(RecentArchive("content://a/1", "a.zip", "ZIP", 1, 1, false, 1, null))
        store.setRememberRecents(false)
        assertThat(store.settings.first().rememberRecents).isFalse()
        assertThat(recents.recents.first()).isEmpty()
        assertThat(released).containsExactly("content://a/1")
    }
}
