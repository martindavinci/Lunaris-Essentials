package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.feature.Routes
import io.github.martindavinci.lunaris.archives.files.CopyResult
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.IncomingCopy
import io.github.martindavinci.lunaris.archives.session.IndexCache
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.session.OpenOutcome
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.session.SourceResult
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.FakeEntryOpener
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserOpeningTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private val engine = FakeArchiveEngine()
    private val opener = FakeEntryOpener()
    private val session by lazy { ArchiveSession(engine, IndexCache(temp.root), PasswordMemory(), IncomingCopy(temp.root)) }
    private val archive = index("notes.md", "inner.zip")
    private val sources = object : SourcesPort {
        override suspend fun open(uri: String) = SourceResult.Ready(source(uri.length))
        override suspend fun copyIn(uri: String, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource = error("unused")
        override fun fromFile(path: String, name: String, uri: String): ArchiveSource = source(path.length)
    }
    private val preferences = object : BrowserPreferences {
        override val settings = flowOf(AppSettings())
        override suspend fun saveSort(order: SortOrder) = Unit
    }
    private val recents = object : RecentsPort { override suspend fun visited(archive: OpenArchive, path: List<String>) = Unit }

    private fun source(n: Int) = ArchiveSource("content://a/$n", "notes.zip", 10, 1, "%016x".format(n), ArchiveKind.ZIP) { error("unused") }
    private fun listed() { engine.lists.getOrPut(EngineRoute.LIBARCHIVE) { ArrayDeque() } += ListResult.Listed(archive) }
    private fun viewModel(id: String = session.register(OpenRequest("content://a/1", null, null, null))) = BrowserViewModel(
        SavedStateHandle(mapOf(Routes.ARG_ARCHIVE_ID to id)), session, sources, preferences, recents, opener, dispatcher,
    )
    private fun BrowserViewModel.tap(name: String) = onAction(BrowserAction.OpenRow(state.value.rows.first { it.name == name }))
    private val ready = CopyResult.Ready("content://x/notes.md", "text/markdown", "/cache/open/notes.md")

    @Test fun tapOnAFile_launchesViewWithoutAnyUiForFastCopies() = runTest(dispatcher) {
        listed(); opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(vm.state.value.opening).isNull()
        assertThat(vm.state.value.launch).isEqualTo(LaunchRequest.View(ready.uri, ready.mime))
    }
    @Test fun slowCopy_showsTheOpeningBarAfter400ms_withProgress() = runTest(dispatcher) {
        listed(); opener.results += ready; opener.gate = CompletableDeferred()
        val vm = viewModel(); advanceUntilIdle()
        opener.progress = 512
        vm.tap("notes.md")
        advanceTimeBy(Limits.OPENING_BAR_DELAY_MILLIS - 1); assertThat(vm.state.value.opening).isNull()
        advanceTimeBy(2); assertThat(vm.state.value.opening!!.name).isEqualTo("notes.md")
        opener.gate!!.complete(Unit); advanceUntilIdle()
        assertThat(vm.state.value.opening).isNull()
    }
    @Test fun cancelOpening_hidesTheBarAndLaunchesNothing() = runTest(dispatcher) {
        listed(); opener.results += ready; opener.gate = CompletableDeferred()
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceTimeBy(Limits.OPENING_BAR_DELAY_MILLIS + 1)
        vm.onAction(BrowserAction.CancelOpening); advanceUntilIdle()
        assertThat(vm.state.value.opening).isNull(); assertThat(vm.state.value.launch).isNull()
    }
    @Test fun noAppForTheFile_showsTheNoAppSheet() = runTest(dispatcher) {
        listed(); opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        vm.onAction(BrowserAction.LaunchHandled(ok = false))
        assertThat(vm.state.value.sheet).isInstanceOf(BrowserSheet.NoApp::class.java)
        assertThat(vm.state.value.launch).isNull()
    }
    @Test fun openWith_usesTheChooser() = runTest(dispatcher) {
        listed(); opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.OpenWith(vm.state.value.rows.first { it.name == "notes.md" })); advanceUntilIdle()
        assertThat(vm.state.value.launch).isEqualTo(LaunchRequest.Chooser(ready.uri, ready.mime))
    }
    @Test fun tapOnANestedArchive_registersARequestAndNavigates() = runTest(dispatcher) {
        listed(); opener.results += ready.copy(path = "/cache/open/inner.zip")
        val id = session.register(OpenRequest("content://a/1", null, null, null))
        val vm = viewModel(id); advanceUntilIdle()
        vm.tap("inner.zip"); advanceUntilIdle()
        val nested = vm.state.value.launch as LaunchRequest.Nested
        assertThat(session.request(nested.requestId)!!.parentId).isEqualTo(id)
        assertThat(session.request(nested.requestId)!!.nestedFile).isEqualTo("/cache/open/inner.zip")
    }
    @Test fun nestedBeyondTheLimit_showsTooDeep() = runTest(dispatcher) {
        var parent: String? = null
        repeat(Limits.MAX_NESTING) { n ->
            listed()
            parent = (session.open(source(n + 1), parentId = parent, nestedEntry = 0) as OpenOutcome.Opened).archive.id
        }
        val vm = viewModel(parent!!); advanceUntilIdle()
        vm.tap("inner.zip"); advanceUntilIdle()
        assertThat(vm.state.value.notice).isEqualTo(BrowserNotice.TooDeep)
        assertThat(opener.calls).isEmpty()
    }
    @Test fun damagedEntry_showsTheDamagedNotice() = runTest(dispatcher) {
        listed(); opener.results += CopyResult.Damaged
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(vm.state.value.notice).isEqualTo(BrowserNotice.Damaged("notes.md"))
    }
    @Test fun changedArchive_relistsAndNotifies() = runTest(dispatcher) {
        listed(); listed(); opener.results += CopyResult.Changed
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(vm.state.value.notice).isEqualTo(BrowserNotice.Changed)
        assertThat(engine.listCalls).hasSize(2)
        assertThat(vm.state.value.loading).isEqualTo(LoadState.Ready)
    }
}
