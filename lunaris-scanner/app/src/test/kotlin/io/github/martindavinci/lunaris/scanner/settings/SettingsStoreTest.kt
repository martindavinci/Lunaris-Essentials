package io.github.martindavinci.lunaris.scanner.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.FailingDataStore
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

    private val store by lazy {
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") },
        )
    }

    @After fun tearDown() = scope.cancel()

    @Test fun defaults_followSystemAndKeepHistoryAndVibrate() = runBlocking {
        assertThat(store.settings.first())
            .isEqualTo(AppSettings(ThemeMode.SYSTEM, keepHistory = true, vibrateOnScan = true))
    }

    @Test fun setters_areStoredIndependently() = runBlocking {
        store.setTheme(ThemeMode.DARK)
        store.setKeepHistory(false)
        store.setVibrateOnScan(false)

        assertThat(store.settings.first())
            .isEqualTo(AppSettings(ThemeMode.DARK, keepHistory = false, vibrateOnScan = false))
    }

    @Test fun unreadableFile_givesTheDefaults() = runBlocking {
        assertThat(SettingsStore(FailingDataStore(readWorks = false)).settings.first()).isEqualTo(AppSettings())
    }

    @Test fun failedWrites_doNotReachTheCaller() = runBlocking {
        val broken = SettingsStore(FailingDataStore(readWorks = true))

        broken.setTheme(ThemeMode.DARK)
        broken.setKeepHistory(false)
        broken.setVibrateOnScan(false)

        assertThat(broken.settings.first()).isEqualTo(AppSettings())
    }
}
