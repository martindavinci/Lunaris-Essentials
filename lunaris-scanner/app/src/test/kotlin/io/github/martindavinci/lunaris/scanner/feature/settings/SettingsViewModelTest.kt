package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.history.HistoryEntry
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.settings.AppSettings
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = SettingsStore(InMemoryPreferences())
    private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var clearCalls = 0

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(store, entries, clearHistory = { clearCalls++ })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.send(viewModel: SettingsViewModel, action: SettingsAction) {
        viewModel.onAction(action)
        advanceUntilIdle()
    }

    @Test fun state_exposesStoredSettings() = runTest {
        store.setTheme(ThemeMode.DARK)

        assertThat(viewModel().state.value.settings.theme).isEqualTo(ThemeMode.DARK)
    }

    @Test fun setTheme_andVibrate_areStored() = runTest {
        val viewModel = viewModel()

        send(viewModel, SettingsAction.SetTheme(ThemeMode.LIGHT))
        send(viewModel, SettingsAction.SetVibrateOnScan(false))

        assertThat(viewModel.state.value.settings)
            .isEqualTo(AppSettings(ThemeMode.LIGHT, keepHistory = true, vibrateOnScan = false))
    }

    @Test fun turningHistoryOff_withEntries_keepsThemAndOffersClearing() = runTest {
        entries.value = listOf(ENTRY)
        val viewModel = viewModel()

        send(viewModel, SettingsAction.SetKeepHistory(false))

        assertThat(viewModel.state.value.settings.keepHistory).isFalse()
        assertThat(viewModel.state.value.offerClearHistory).isTrue()
        assertThat(clearCalls).isEqualTo(0)
    }

    @Test fun turningHistoryOff_withNoEntries_offersNothing() = runTest {
        val viewModel = viewModel()

        send(viewModel, SettingsAction.SetKeepHistory(false))

        assertThat(viewModel.state.value.settings.keepHistory).isFalse()
        assertThat(viewModel.state.value.offerClearHistory).isFalse()
    }

    @Test fun turningHistoryOn_offersNothing() = runTest {
        entries.value = listOf(ENTRY)
        store.setKeepHistory(false)
        val viewModel = viewModel()

        send(viewModel, SettingsAction.SetKeepHistory(true))

        assertThat(viewModel.state.value.settings.keepHistory).isTrue()
        assertThat(viewModel.state.value.offerClearHistory).isFalse()
    }

    @Test fun clearHistory_clearsAndClosesTheOffer() = runTest {
        entries.value = listOf(ENTRY)
        val viewModel = viewModel()
        send(viewModel, SettingsAction.SetKeepHistory(false))

        send(viewModel, SettingsAction.ClearHistory)

        assertThat(clearCalls).isEqualTo(1)
        assertThat(viewModel.state.value.offerClearHistory).isFalse()
    }

    @Test fun dismissingTheOffer_keepsEntries() = runTest {
        entries.value = listOf(ENTRY)
        val viewModel = viewModel()
        send(viewModel, SettingsAction.SetKeepHistory(false))

        send(viewModel, SettingsAction.DismissClearHistory)

        assertThat(clearCalls).isEqualTo(0)
        assertThat(viewModel.state.value.offerClearHistory).isFalse()
    }

    /** Never suspends, so the tests depend only on the test scheduler, not on a file or real time. */
    private class InMemoryPreferences : DataStore<Preferences> {
        private val current = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = current

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(current.value).also { current.value = it }
    }

    private companion object {
        val ENTRY = HistoryEntry(id = 1, text = "hello", format = CodeFormat.QR_CODE, timestampMillis = 1)
    }
}
