package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.browse.SortField
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.feature.Routes
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.IncomingCopy
import io.github.martindavinci.lunaris.archives.session.IndexCache
import io.github.martindavinci.lunaris.archives.session.OpenArchive
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
class BrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private val engine = FakeArchiveEngine()
    private val session by lazy { ArchiveSession(engine, IndexCache(temp.root), PasswordMemory(), IncomingCopy(temp.root)) }
    private val saved = mutableListOf<SortOrder>()
    private val visits = mutableListOf<List<String>>()
    private val archive = index("README.md", "docs/a.md", "docs/sub/b.md", "file10.txt", "file2.txt", "../x")
    private val sources = object : SourcesPort {
        override suspend fun open(uri: String) =
            SourceResult.Ready(ArchiveSource(uri, "notes.zip", 10, 1, "%016x".format(uri.length), ArchiveKind.ZIP) { error("unused") })
        override suspend fun copyIn(uri: String, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource = error("unused")
        override fun fromFile(path: String, name: String, uri: String) =
            ArchiveSource(uri, name, 10, 1, "%016x".format(path.length), ArchiveKind.ZIP) { error("unused") }
    }
    private val preferences = object : BrowserPreferences {
        override val settings = flowOf(AppSettings())
        override suspend fun saveSort(order: SortOrder) { saved += order }
    }
    private val recents = object : RecentsPort {
        override suspend fun visited(archive: OpenArchive, path: List<String>) { visits += path }
    }

    private fun viewModel(id: String = session.register(OpenRequest("content://a/1", null, null, null))) = BrowserViewModel(
        SavedStateHandle(mapOf(Routes.ARG_ARCHIVE_ID to id)), session, sources, preferences, recents, FakeEntryOpener(), dispatcher,
    )
    private fun listed(result: ListResult = ListResult.Listed(archive)) {
        engine.lists.getOrPut(EngineRoute.LIBARCHIVE) { ArrayDeque() } += result
    }
    private fun BrowserViewModel.names() = state.value.rows.map { it.name }
    private fun BrowserViewModel.row(name: String) = state.value.rows.first { it.name == name }

    @Test fun opensAndShowsTheRootSortedFoldersFirst() = runTest(dispatcher) {
        listed()
        val vm = viewModel(); advanceUntilIdle()
        assertThat(vm.names()).containsExactly("docs", "file2.txt", "file10.txt", "README.md").inOrder()
        assertThat(vm.state.value.hiddenUnsafe).isEqualTo(1)
        assertThat(vm.state.value.loading).isEqualTo(LoadState.Ready)
        assertThat(vm.state.value.header!!.fileCount).isEqualTo(5)
    }
    @Test fun continueOpensAtTheRememberedFolder_andFallsBackToTheRoot() = runTest(dispatcher) {
        listed(); listed()
        val there = viewModel(session.register(OpenRequest("content://a/1", null, null, null, startPath = listOf("docs", "sub")))); advanceUntilIdle()
        assertThat(there.state.value.path).containsExactly("docs", "sub").inOrder()
        val gone = viewModel(session.register(OpenRequest("content://a/2", null, null, null, startPath = listOf("nope")))); advanceUntilIdle()
        assertThat(gone.state.value.path).isEmpty()
    }
    @Test fun extractingProtectedEntriesWithoutAPassword_asksFirst_thenExtractsWithIt() = runTest(dispatcher) {
        listed(ListResult.Listed(index("a.md", "b.md", encrypted = setOf("b.md"))))
        val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.ExtractAll)
        assertThat(vm.state.value.password?.target).isEqualTo(PasswordTarget.EXTRACT)
        assertThat(vm.state.value.launch).isNull()
        vm.onAction(BrowserAction.SubmitPassword("pw".toCharArray(), remember = false)); advanceUntilIdle()
        assertThat(vm.state.value.password).isNull()
        assertThat(vm.state.value.launch).isInstanceOf(LaunchRequest.Extract::class.java)
        assertThat(String(session.passwordFor((vm.state.value.launch as LaunchRequest.Extract).jobArgs.archiveId)!!)).isEqualTo("pw")
    }
    @Test fun dismissingTheExtractPassword_extractsNothing_andStays() = runTest(dispatcher) {
        listed(ListResult.Listed(index("a.md", encrypted = setOf("a.md"))))
        val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.ExtractAll); vm.onAction(BrowserAction.DismissPassword)
        assertThat(vm.state.value.launch).isNull(); assertThat(vm.state.value.exit).isFalse()
    }
    @Test fun extractingPlainEntries_needsNoPassword() = runTest(dispatcher) {
        listed()
        val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.ExtractAll)
        assertThat(vm.state.value.password).isNull(); assertThat(vm.state.value.launch).isNotNull()
    }
    @Test fun extractAllFromHome_asksForTheFolderOnceOpened() = runTest(dispatcher) {
        listed()
        val vm = viewModel(session.register(OpenRequest("content://a/1", null, null, null, extractAll = true))); advanceUntilIdle()
        val launch = vm.state.value.launch as LaunchRequest.Extract
        assertThat(launch.jobArgs.indices.size).isEqualTo(archive.entries.size)
    }
    @Test fun readingHintAppearsOnlyAfter300ms() = runTest(dispatcher) {
        listed(); engine.listGate = CompletableDeferred()
        val vm = viewModel()
        advanceTimeBy(299); assertThat(vm.state.value.loading).isEqualTo(LoadState.Reading)
        advanceTimeBy(2); assertThat(vm.state.value.loading).isInstanceOf(LoadState.ReadingSlow::class.java)
        engine.listGate!!.complete(Unit); advanceUntilIdle()
        assertThat(vm.state.value.loading).isEqualTo(LoadState.Ready)
    }
    @Test fun aFolderIsSortedInTheBackground() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.OpenRow(vm.row("docs")))
        assertThat(vm.state.value.path).isEmpty()
        advanceUntilIdle()
        assertThat(vm.state.value.path).containsExactly("docs")
    }
    @Test fun leavingTheBrowser_closesItsArchive() = runTest(dispatcher) {
        listed()
        val id = session.register(OpenRequest("content://a/1", null, null, null))
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(id) } })[BrowserViewModel::class]
        advanceUntilIdle()
        assertThat(session.get(id)).isNotNull()
        store.clear()
        assertThat(session.get(id)).isNull()
    }
    @Test fun aNestedArchive_isSharedThroughItsContentUri() = runTest(dispatcher) {
        listed()
        val vm = viewModel(session.register(OpenRequest("content://app.files/open/k/inner.zip", null, 0, "/cache/open/k/inner.zip"))); advanceUntilIdle()
        vm.onAction(BrowserAction.ShareArchive)
        assertThat((vm.state.value.launch as LaunchRequest.Send).uris).containsExactly("content://app.files/open/k/inner.zip")
    }
    @Test fun openRowOfAFolder_entersIt_andUpGoesBack() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.OpenRow(vm.row("docs"))); advanceUntilIdle()
        assertThat(vm.state.value.path).containsExactly("docs")
        assertThat(vm.state.value.crumbs.map { it.label }).containsExactly("notes.zip", "docs").inOrder()
        vm.onAction(BrowserAction.Up); advanceUntilIdle()
        assertThat(vm.state.value.path).isEmpty()
    }
    @Test fun upAtTheRoot_requestsExit() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.Up)
        assertThat(vm.state.value.exit).isTrue()
    }
    @Test fun goToCrumb_jumps() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.OpenRow(vm.row("docs"))); advanceUntilIdle()
        vm.onAction(BrowserAction.OpenRow(vm.row("sub"))); advanceUntilIdle()
        vm.onAction(BrowserAction.GoToCrumb(1)); advanceUntilIdle()
        assertThat(vm.state.value.path).containsExactly("docs")
    }
    @Test fun setSort_appliesAndSaves() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        val bySize = SortOrder(SortField.SIZE, ascending = false)
        vm.onAction(BrowserAction.SetSort(bySize)); advanceUntilIdle()
        assertThat(vm.state.value.sort).isEqualTo(bySize)
        assertThat(saved).containsExactly(bySize)
    }
    @Test fun search_findsAcrossFolders_andClosingRestores() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        val before = vm.names()
        vm.onAction(BrowserAction.OpenSearch); vm.onAction(BrowserAction.SetQuery("b")); advanceUntilIdle()
        assertThat(vm.state.value.hits.map { it.row.path.joinToString("/") }).contains("docs/sub/b.md")
        vm.onAction(BrowserAction.CloseSearch)
        assertThat(vm.state.value.hits).isEmpty(); assertThat(vm.state.value.query).isNull(); assertThat(vm.names()).isEqualTo(before)
    }
    @Test fun longPressStartsSelection_toggleAndSelectAll() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.LongPress(vm.row("file2.txt")))
        assertThat(vm.state.value.selection).containsExactly(vm.row("file2.txt").entry!!.index)
        vm.onAction(BrowserAction.Toggle(vm.row("file10.txt")))
        assertThat(vm.state.value.selection).hasSize(2)
        vm.onAction(BrowserAction.SelectAll)
        assertThat(vm.state.value.selectedFolders).containsExactly(listOf("docs"))
        assertThat(vm.state.value.selection).hasSize(3)
        vm.onAction(BrowserAction.ClearSelection)
        assertThat(vm.state.value.selecting).isFalse()
    }
    @Test fun selectionWithAFolder_hidesShare() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.LongPress(vm.row("file2.txt")))
        assertThat(vm.state.value.canShareSelection).isTrue()
        vm.onAction(BrowserAction.Toggle(vm.row("docs")))
        assertThat(vm.state.value.canShareSelection).isFalse()
    }
    @Test fun problemsMapToProblemState() = runTest(dispatcher) {
        listed(ListResult.Problem(ArchiveProblem.RAR4_PASSWORD)); val vm = viewModel(); advanceUntilIdle()
        assertThat(vm.state.value.problem).isEqualTo(ArchiveProblem.RAR4_PASSWORD)
        assertThat(vm.state.value.header).isNull()
    }
    @Test fun cappedIndex_isFlagged() = runTest(dispatcher) {
        listed(ListResult.Listed(archive.copy(cappedAtLimit = true))); val vm = viewModel(); advanceUntilIdle()
        assertThat(vm.state.value.capped).isTrue()
    }
    @Test fun unknownRequestId_isGone() = runTest(dispatcher) {
        val vm = viewModel(id = "nope"); advanceUntilIdle()
        assertThat(vm.state.value.problem).isEqualTo(ArchiveProblem.GONE)
    }
    @Test fun visitedIsReportedOnceListed_withThePath() = runTest(dispatcher) {
        listed(); val vm = viewModel(); advanceUntilIdle()
        assertThat(visits).containsExactly(emptyList<String>())
        vm.onAction(BrowserAction.OpenRow(vm.row("docs"))); advanceTimeBy(500)
        assertThat(visits).hasSize(1)
        advanceUntilIdle()
        assertThat(visits.last()).containsExactly("docs")
    }
}
