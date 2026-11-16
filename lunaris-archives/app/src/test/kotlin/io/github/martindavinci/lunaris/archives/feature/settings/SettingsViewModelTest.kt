package io.github.martindavinci.lunaris.archives.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.files.CacheJanitor
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.recents.RecentsStore
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
import io.github.martindavinci.lunaris.archives.settings.SettingsStore
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "s.preferences_pb") } }
    private val recents by lazy { RecentsStore(dataStore) }
    private val permissions = object : UriPermissions {
        override fun takeRead(uri: String) = true
        override fun release(uri: String) = Unit
    }
    private val store by lazy { SettingsStore(dataStore, recents, permissions) }
    private val cache by lazy { temp.newFolder("cache") }
    private val janitor by lazy { CacheJanitor(cache) }
    private val models = ViewModelStore()
    private fun viewModel(): SettingsViewModel = ViewModelProvider.create(
        models, viewModelFactory { initializer { SettingsViewModel(store, janitor, Dispatchers.IO) } },
    )[SettingsViewModel::class]

    /** The view model writes on its own scope: wait for the stored value to show up. */
    private fun <T> eventually(read: () -> T, wanted: T) {
        repeat(ATTEMPTS) { if (read() == wanted) return; Thread.sleep(STEP_MILLIS) }
        assertThat(read()).isEqualTo(wanted)
    }

    /** The view models stop reading before the store's scope ends, or a late failure lands in another test. */
    @After fun stop() = runBlocking {
        models.clear()
        scope.coroutineContext.job.cancelAndJoin()
    }

    @Test fun themeChange_isSaved() {
        viewModel().onAction(SettingsAction.SetTheme(ThemeMode.LIGHT))
        eventually({ runBlocking { store.settings.first().theme } }, ThemeMode.LIGHT)
    }
    @Test fun rememberOff_isSaved_andClearsRecents() {
        runBlocking { recents.remember(RecentArchive("content://a/1", "a.zip", "ZIP", 1, 1, false, 1, null)) }
        viewModel().onAction(SettingsAction.SetRememberRecents(false))
        eventually({ runBlocking { store.settings.first().rememberRecents to recents.recents.first().size } }, false to 0)
    }
    @Test fun clearTemporaryFiles_emptiesTheCache_andSaysSo() {
        File(cache, "open/k/1").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(SIZE)) }
        File(cache, "index/k.idx").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(SIZE)) }
        val vm = viewModel()
        eventually({ vm.state.value.tempBytes }, 2L * SIZE)
        vm.onAction(SettingsAction.ClearTemporaryFiles)
        eventually({ vm.state.value.tempBytes to vm.state.value.notice }, 0L to SettingsNotice.Cleared)
        assertThat(File(cache, "open").exists()).isFalse()
    }

    private companion object {
        const val SIZE = 100
        const val ATTEMPTS = 100
        const val STEP_MILLIS = 20L
    }
}
