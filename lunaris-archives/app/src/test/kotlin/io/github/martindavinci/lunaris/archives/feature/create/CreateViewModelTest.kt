package io.github.martindavinci.lunaris.archives.feature.create

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.create.ArchiveNameRules
import io.github.martindavinci.lunaris.archives.create.Child
import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.CreateIo
import io.github.martindavinci.lunaris.archives.create.CreateRunner
import io.github.martindavinci.lunaris.archives.create.CreateState
import io.github.martindavinci.lunaris.archives.create.Described
import io.github.martindavinci.lunaris.archives.create.SourceTree
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.CreateInbox
import io.github.martindavinci.lunaris.archives.session.IncomingCopy
import io.github.martindavinci.lunaris.archives.session.IndexCache
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.testing.CountingDispatcher
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private val tree = object : SourceTree {
        override fun describe(uri: String) = Described(uri.substringAfterLast('/'), 10, 0, isVirtual = false)
        override fun rootOf(treeUri: String) = "root" to "Holiday"
        override fun children(treeUri: String, documentId: String) =
            if (documentId == "root") listOf(Child("a", "a.jpg", false, 5, 0, false), Child("b", "b.jpg", false, 7, 0, false)) else emptyList()
        override fun uriOf(treeUri: String, documentId: String) = "doc:$documentId"
    }
    private val io = object : CreateIo {
        override fun openInput(uri: String) = ByteArray(3).inputStream()
        override fun openOutput(uri: String) = ByteArrayOutputStream()
        override fun delete(uri: String) = Unit
        override fun size(uri: String): Long? = 42
        override fun location(uri: String): String? = "Download"
    }
    private val runner = CreateRunner(io, dispatcher)
    private val inbox = CreateInbox()
    private val session by lazy { ArchiveSession(FakeArchiveEngine(), IndexCache(temp.root), PasswordMemory(), IncomingCopy(temp.root)) }
    private val background = CountingDispatcher()
    private fun viewModel() = CreateViewModel(tree, runner, inbox, session, background)

    @Test fun startsWithJustTheTwoCards() = runTest(dispatcher) {
        val s = viewModel().state.value
        assertThat(s.items).isEmpty(); assertThat(s.canCreate).isFalse(); assertThat(s.protect).isFalse()
    }
    @Test fun addingFiles_listsThem_summarises_andSuggestsAName() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/photo.jpg")))
        val s = vm.state.value
        assertThat(s.items.map { it.name }).containsExactly("photo.jpg"); assertThat(s.totalBytes).isEqualTo(10)
        assertThat(s.name).isEqualTo("photo"); assertThat(s.canCreate).isTrue(); assertThat(s.pickedFiles).isTrue()
    }
    @Test fun addingAFolder_addsOneRowWithItsCount() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFolder("tree:Holiday"))
        val item = vm.state.value.items.single()
        assertThat(item.fileCount).isEqualTo(2); assertThat(item.isFolder).isTrue(); assertThat(vm.state.value.name).isEqualTo("Holiday")
    }
    @Test fun foldersAndFilesAreReadInTheBackground() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFolder("tree:Holiday")); vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt")))
        assertThat(background.used).isEqualTo(2)
    }
    @Test fun removingAnItem_updatesTheTotal() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt", "content://x/b.txt")))
        vm.onAction(CreateAction.Remove(vm.state.value.items.first()))
        assertThat(vm.state.value.items).hasSize(1); assertThat(vm.state.value.totalBytes).isEqualTo(10)
    }
    @Test fun invalidName_disablesCreate_andShowsTheError() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt"))); vm.onAction(CreateAction.SetName("a/b"))
        assertThat(vm.state.value.nameError).isTrue(); assertThat(vm.state.value.canCreate).isFalse()
    }
    @Test fun passwordMismatch_disablesCreate() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt"))); vm.onAction(CreateAction.SetProtect(true))
        vm.onAction(CreateAction.PasswordChanged(level = 3, matches = false))
        assertThat(vm.state.value.mismatch).isTrue(); assertThat(vm.state.value.canCreate).isFalse()
        vm.onAction(CreateAction.PasswordChanged(level = 3, matches = true))
        assertThat(vm.state.value.canCreate).isTrue()
    }
    @Test fun sharedFiles_arePrefilled_withSharedName() = runTest(dispatcher) {
        inbox.put(listOf("content://x/1.jpg", "content://x/2.jpg", "content://x/3.jpg"))
        val s = viewModel().state.value
        assertThat(s.items).hasSize(3); assertThat(s.name).isEqualTo(ArchiveNameRules.SHARED_FILES_FALLBACK)
    }
    @Test fun createAt_startsTheRunner_withTheChosenLevel() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt"))); vm.onAction(CreateAction.SetLevel(CompressionLevel.MAXIMUM))
        vm.onAction(CreateAction.CreateAt("content://out/a.zip", null)); advanceUntilIdle()
        assertThat(runner.state.value).isInstanceOf(CreateState.Finished::class.java)
        assertThat(vm.state.value.state).isInstanceOf(CreateState.Finished::class.java)
    }
    @Test fun openCreated_navigatesToTheBrowserOfTheNewArchive() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(CreateAction.AddFiles(listOf("content://x/a.txt"))); vm.onAction(CreateAction.CreateAt("content://out/a.zip", null)); advanceUntilIdle()
        vm.onAction(CreateAction.OpenCreated)
        val id = vm.state.value.openRequest!!
        assertThat(session.request(id)!!.uri).isEqualTo("content://out/a.zip")
        assertThat(runner.state.value).isEqualTo(CreateState.Idle)
    }
}
