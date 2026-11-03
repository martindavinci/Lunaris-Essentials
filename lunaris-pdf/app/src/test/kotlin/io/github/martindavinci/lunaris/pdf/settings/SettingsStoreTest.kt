package io.github.martindavinci.lunaris.pdf.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy {
        SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") })
    }

    @After fun tearDown() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    @Test fun nothingStored_givesTheDefaults() = runBlocking {
        assertThat(store.settings.first()).isEqualTo(AppSettings())
        assertThat(store.settings.first().topButton).isTrue()
    }

    @Test fun choices_areKept() = runBlocking {
        store.setTheme(ThemeMode.DARK)
        store.setNightPages(true)
        store.setTopButton(false)
        assertThat(store.settings.first())
            .isEqualTo(AppSettings(ThemeMode.DARK, nightPages = true, topButton = false))
    }

    @Test fun speech_startsNormal_andIsRemembered() = runBlocking {
        assertThat(store.speechRate.first()).isEqualTo(1f)
        assertThat(store.speechVoice.first()).isNull()
        store.setSpeechRate(2.5f)
        store.setSpeechVoice("it-it-x-itc-local")
        assertThat(store.speechRate.first()).isEqualTo(2.5f)
        assertThat(store.speechVoice.first()).isEqualTo("it-it-x-itc-local")
        store.setSpeechVoice(null)
        assertThat(store.speechVoice.first()).isNull()
    }
}
