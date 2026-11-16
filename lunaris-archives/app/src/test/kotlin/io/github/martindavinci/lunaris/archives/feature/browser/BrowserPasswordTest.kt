package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.ListResult
import io.github.martindavinci.lunaris.archives.feature.Routes
import io.github.martindavinci.lunaris.archives.files.CopyResult
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserPasswordTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private val engine = FakeArchiveEngine()
    private val opener = FakeEntryOpener()
    private val memory = PasswordMemory()
    private val session by lazy { ArchiveSession(engine, IndexCache(temp.root), memory, IncomingCopy(temp.root)) }
    private val key = "%016x".format(1)
    private val sources = object : SourcesPort {
        override suspend fun open(uri: String) = SourceResult.Ready(ArchiveSource(uri, "secret.7z", 10, 1, key, ArchiveKind.SEVEN_Z) { error("unused") })
        override suspend fun copyIn(uri: String, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource = error("unused")
        override fun fromFile(path: String, name: String, uri: String): ArchiveSource = error("unused")
    }
    private val preferences = object : BrowserPreferences {
        override val settings = flowOf(AppSettings())
        override suspend fun saveSort(order: SortOrder) = Unit
    }
    private val recents = object : RecentsPort { override suspend fun visited(archive: OpenArchive, path: List<String>) = Unit }
    private val ready = CopyResult.Ready("content://x/notes.md", "text/markdown", "/cache/open/notes.md")

    private fun answer(route: EngineRoute, vararg results: ListResult) { engine.lists.getOrPut(route) { ArrayDeque() }.addAll(results) }
    private fun viewModel() = BrowserViewModel(
        SavedStateHandle(mapOf(Routes.ARG_ARCHIVE_ID to session.register(OpenRequest("content://a/1", null, null, null)))),
        session, sources, preferences, recents, opener, dispatcher,
    )
    private fun BrowserViewModel.tap(name: String) = onAction(BrowserAction.OpenRow(state.value.rows.first { it.name == name }))
    private fun BrowserViewModel.submit(text: String, remember: Boolean = false): CharArray =
        text.toCharArray().also { onAction(BrowserAction.SubmitPassword(it, remember)) }
    private fun headersLocked() = answer(EngineRoute.LIBARCHIVE, ListResult.NeedsPassword(EngineRoute.SEVEN_Z))
    private fun plainList() = answer(EngineRoute.LIBARCHIVE, ListResult.Listed(index("notes.md", encrypted = setOf("notes.md"))))

    @Test fun encryptedHeaders_showThePromptWithAnEmptyList() = runTest(dispatcher) {
        headersLocked(); val vm = viewModel(); advanceUntilIdle()
        assertThat(vm.state.value.password).isEqualTo(PasswordPrompt("secret.7z", PasswordTarget.ARCHIVE))
        assertThat(vm.state.value.rows).isEmpty(); assertThat(vm.state.value.title).isEqualTo("secret.7z")
    }
    @Test fun submit_showsChecking_thenOpens() = runTest(dispatcher) {
        headersLocked(); answer(EngineRoute.SEVEN_Z, ListResult.Listed(index("a.txt")))
        val vm = viewModel(); advanceUntilIdle()
        engine.listGate = CompletableDeferred()
        vm.submit("pw"); runCurrent()
        assertThat(vm.state.value.password!!.checking).isTrue()
        engine.listGate!!.complete(Unit); advanceUntilIdle()
        assertThat(vm.state.value.password).isNull(); assertThat(vm.state.value.loading).isEqualTo(LoadState.Ready)
    }
    @Test fun wrongPassword_marksTheField() = runTest(dispatcher) {
        headersLocked(); answer(EngineRoute.SEVEN_Z, ListResult.WrongPassword)
        val vm = viewModel(); advanceUntilIdle()
        vm.submit("bad"); advanceUntilIdle()
        assertThat(vm.state.value.password).isEqualTo(PasswordPrompt("secret.7z", PasswordTarget.ARCHIVE, wrong = true, checking = false))
    }
    @Test fun submittedArrayIsWiped() = runTest(dispatcher) {
        headersLocked(); answer(EngineRoute.SEVEN_Z, ListResult.Listed(index("a.txt")))
        val vm = viewModel(); advanceUntilIdle()
        val typed = vm.submit("pw"); advanceUntilIdle()
        assertThat(typed.all { it == Char(0) }).isTrue()
    }
    @Test fun remember_isPassedToTheSession() = runTest(dispatcher) {
        headersLocked(); answer(EngineRoute.SEVEN_Z, ListResult.Listed(index("a.txt")))
        val vm = viewModel(); advanceUntilIdle()
        vm.submit("pw", remember = true); advanceUntilIdle()
        assertThat(memory.get(key)!!.concatToString()).isEqualTo("pw")
    }
    @Test fun dismissAtListTime_leavesTheBrowser() = runTest(dispatcher) {
        headersLocked(); val vm = viewModel(); advanceUntilIdle()
        vm.onAction(BrowserAction.DismissPassword)
        assertThat(vm.state.value.exit).isTrue()
    }
    @Test fun encryptedEntryTap_asksForThePasswordWithTheEntryName() = runTest(dispatcher) {
        plainList(); opener.results += CopyResult.NeedsPassword
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(vm.state.value.password).isEqualTo(PasswordPrompt("notes.md", PasswordTarget.ENTRY))
    }
    @Test fun entryPassword_retriesTheSameEntry_andOpensIt() = runTest(dispatcher) {
        plainList(); opener.results += CopyResult.NeedsPassword; opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        vm.submit("pw"); advanceUntilIdle()
        assertThat(opener.calls.last()).isEqualTo(0L to "pw")
        assertThat(vm.state.value.launch).isEqualTo(LaunchRequest.View(ready.uri, ready.mime))
        assertThat(vm.state.value.password).isNull()
    }
    @Test fun anEntryPassword_staysForTheNextTap() = runTest(dispatcher) {
        plainList(); opener.results += CopyResult.NeedsPassword; opener.results += ready; opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle(); vm.submit("pw"); advanceUntilIdle()
        vm.onAction(BrowserAction.LaunchHandled(true)); vm.tap("notes.md"); advanceUntilIdle()
        assertThat(opener.calls.last()).isEqualTo(0L to "pw")
        assertThat(memory.get(key)).isNull()
    }
    @Test fun rememberedPassword_isUsedForEntryTapsWithoutAsking() = runTest(dispatcher) {
        memory.put(key, "kept".toCharArray()); plainList(); opener.results += ready
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(opener.calls.single()).isEqualTo(0L to "kept")
    }
    @Test fun rar4EncryptedEntry_showsTheRar4PanelNotAPrompt() = runTest(dispatcher) {
        plainList(); opener.results += CopyResult.Rar4Password
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.Rar4); assertThat(vm.state.value.password).isNull()
    }
    @Test fun dismissAtEntryTime_justClosesTheDialog() = runTest(dispatcher) {
        plainList(); opener.results += CopyResult.NeedsPassword
        val vm = viewModel(); advanceUntilIdle()
        vm.tap("notes.md"); advanceUntilIdle()
        vm.onAction(BrowserAction.DismissPassword)
        assertThat(vm.state.value.password).isNull(); assertThat(vm.state.value.exit).isFalse()
    }
}
