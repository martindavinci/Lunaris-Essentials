package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.history.HistoryRepository
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.stopCoroutinesAndResetMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") }
    }
    private val settings by lazy { SettingsStore(dataStore) }
    private val repository by lazy { HistoryRepository(dataStore, settings) }
    private val currentResult = CurrentResult()
    private val viewModels = mutableListOf<HistoryViewModel>()

    private fun viewModel() = HistoryViewModel(repository, settings, currentResult).also { viewModels += it }

    private suspend fun HistoryViewModel.await(predicate: (HistoryUiState) -> Boolean): HistoryUiState =
        withTimeout(TIMEOUT_MILLIS) { state.first(predicate) }

    private suspend fun add(text: String) = repository.add(ScannedCode(text, CodeFormat.QR_CODE))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = stopCoroutinesAndResetMain(viewModels, scope)

    @Test fun notLoaded_untilTheStoreHasBeenRead() {
        assertThat(HistoryUiState().loaded).isFalse()
    }

    @Test fun loads_theEntriesNewestFirst() = runBlocking<Unit> {
        add("first")
        add("second")

        val loaded = viewModel().await { it.loaded }

        assertThat(loaded.visibleItems.map { it.summary }).containsExactly("second", "first").inOrder()
    }

    @Test fun followsNewEntries() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.await { it.loaded }

        add("late")

        assertThat(viewModel.await { it.allItems.isNotEmpty() }.allItems.single().summary).isEqualTo("late")
    }

    @Test fun query_filtersAtOnceAndKeepsTheFullList() = runBlocking<Unit> {
        add("apple")
        add("pear")
        val viewModel = viewModel()
        viewModel.await { it.allItems.size == 2 }

        viewModel.onAction(HistoryAction.QueryChanged("app"))

        val state = viewModel.state.value
        assertThat(state.query).isEqualTo("app")
        assertThat(state.visibleItems.map { it.summary }).containsExactly("apple")
        assertThat(state.allItems).hasSize(2)
    }

    @Test fun wifi_showsTheSsidAndIsNotFoundByPassword() = runBlocking<Unit> {
        add("WIFI:T:WPA;S:Home;P:s3cret;;")
        val viewModel = viewModel()

        val item = viewModel.await { it.loaded }.allItems.single()
        assertThat(item.kind).isEqualTo(HistoryKind.WIFI)
        assertThat(item.summary).isEqualTo("Home")

        viewModel.onAction(HistoryAction.QueryChanged("s3cret"))
        assertThat(viewModel.state.value.visibleItems).isEmpty()
    }

    @Test fun open_setsTheCurrentResult() = runBlocking<Unit> {
        add("hello")
        val viewModel = viewModel()
        val item = viewModel.await { it.loaded }.allItems.single()

        viewModel.onAction(HistoryAction.Open(item))

        assertThat(currentResult.code.value).isEqualTo(ScannedCode("hello", CodeFormat.QR_CODE))
    }

    @Test fun delete_removesTheEntry() = runBlocking<Unit> {
        add("gone")
        val viewModel = viewModel()
        val item = viewModel.await { it.loaded }.allItems.single()

        viewModel.onAction(HistoryAction.Delete(item.id))

        viewModel.await { it.allItems.isEmpty() }
        assertThat(repository.entries.first()).isEmpty()
    }

    @Test fun clear_asksFirstAndCancelKeepsEverything() = runBlocking<Unit> {
        add("kept")
        val viewModel = viewModel()
        viewModel.await { it.loaded }

        viewModel.onAction(HistoryAction.RequestClear)
        assertThat(viewModel.state.value.confirmingClear).isTrue()
        viewModel.onAction(HistoryAction.DismissClear)

        assertThat(viewModel.state.value.confirmingClear).isFalse()
        assertThat(repository.entries.first()).hasSize(1)
    }

    @Test fun clear_confirmed_removesEverything() = runBlocking<Unit> {
        add("a")
        add("b")
        val viewModel = viewModel()
        viewModel.await { it.allItems.size == 2 }

        viewModel.onAction(HistoryAction.RequestClear)
        viewModel.onAction(HistoryAction.ConfirmClear)

        val cleared = viewModel.await { it.allItems.isEmpty() }
        assertThat(cleared.confirmingClear).isFalse()
    }

    @Test fun keepHistory_followsTheSetting() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertThat(viewModel.await { it.loaded }.keepHistory).isTrue()

        settings.setKeepHistory(false)

        viewModel.await { !it.keepHistory }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
