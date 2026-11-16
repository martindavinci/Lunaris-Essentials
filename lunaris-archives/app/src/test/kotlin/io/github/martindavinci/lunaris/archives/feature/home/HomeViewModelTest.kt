package io.github.martindavinci.lunaris.archives.feature.home

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.extract.KeyValue
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.recents.RecentsRepository
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.IncomingCopy
import io.github.martindavinci.lunaris.archives.session.IndexCache
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    @get:Rule val temp = TemporaryFolder()
    private fun item(n: Int, name: String = "a$n.zip", path: List<String>? = null) =
        RecentArchive("content://a/$n", name, "ZIP", 1, 1, false, n.toLong(), path)
    private val repo = object : RecentsRepository {
        override val recents = MutableStateFlow(listOf(item(3, "Project notes.zip", listOf("docs")), item(2, "Photos.zip"), item(1, "old.rar")))
        override suspend fun remove(uri: String) = recents.value.firstOrNull { it.uri == uri }.also { recents.value = recents.value.filter { it.uri != uri } }
        override suspend fun restore(item: RecentArchive) { recents.value = (recents.value + item).sortedByDescending { it.lastOpenedMillis } }
        override suspend fun clear() = recents.value.also { recents.value = emptyList() }
    }
    private val settings = MutableStateFlow(AppSettings())
    private val released = mutableListOf<String>()
    private val permissions = object : UriPermissions {
        override fun takeRead(uri: String) = true
        override fun release(uri: String) { released += uri }
    }
    private var readable = true
    private val marker = PrefsExtractMarker(object : KeyValue {
        override fun get(): String? = null
        override fun put(value: String) = Unit
        override fun remove() = Unit
    })
    private val session by lazy { ArchiveSession(FakeArchiveEngine(), IndexCache(temp.root), PasswordMemory(), IncomingCopy(temp.root)) }
    private fun viewModel() = HomeViewModel(repo, settings, permissions, session, { readable }, marker)

    @Test fun showsRecentsNewestFirst_andTheFirstAsContinue() = runTest(dispatcher) {
        val s = viewModel().state.value
        assertThat(s.entries.map { it.name }).containsExactly("Project notes.zip", "Photos.zip", "old.rar").inOrder()
        assertThat(s.continueItem?.name).isEqualTo("Project notes.zip")
    }
    @Test fun emptyRecents_haveNoContinue() = runTest(dispatcher) {
        repo.recents.value = emptyList()
        assertThat(viewModel().state.value.continueItem).isNull()
    }
    @Test fun search_filtersByName_caseInsensitive() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.OpenSearch); vm.onAction(HomeAction.SetQuery("PHOTO"))
        assertThat(vm.state.value.entries.map { it.name }).containsExactly("Photos.zip")
    }
    @Test fun openPicked_registersARequest_andNavigates() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.OpenPicked("content://new/1"))
        val nav = vm.state.value.navigate as HomeNav.Browser
        assertThat(session.request(nav.requestId)!!.uri).isEqualTo("content://new/1")
    }
    @Test fun openRecent_thatCannotBeRead_removesIt_andSaysGone() = runTest(dispatcher) {
        readable = false
        val vm = viewModel()
        vm.onAction(HomeAction.OpenRecent(item(2, "Photos.zip")))
        assertThat(vm.state.value.notice).isEqualTo(HomeNotice.Gone); assertThat(vm.state.value.navigate).isNull()
        assertThat(repo.recents.value.map { it.uri }).doesNotContain("content://a/2")
    }
    @Test fun continue_opensAtTheLastPath() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.OpenRecent(vm.state.value.continueItem!!))
        val id = (vm.state.value.navigate as HomeNav.Browser).requestId
        assertThat(session.request(id)!!.startPath).containsExactly("docs")
    }
    @Test fun remove_showsUndo_andUndoRestores() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.Remove(item(2, "Photos.zip")))
        assertThat(vm.state.value.notice).isEqualTo(HomeNotice.Removed(item(2, "Photos.zip")))
        vm.onAction(HomeAction.UndoRemove)
        assertThat(repo.recents.value.map { it.name }).containsExactly("Project notes.zip", "Photos.zip", "old.rar").inOrder()
    }
    @Test fun clear_asksFirst_thenClears_andReleasesPermissions() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.AskClear)
        assertThat(vm.state.value.confirmClear).isTrue(); assertThat(repo.recents.value).hasSize(3)
        vm.onAction(HomeAction.ConfirmClear)
        assertThat(repo.recents.value).isEmpty(); assertThat(released).hasSize(3); assertThat(vm.state.value.confirmClear).isFalse()
    }
    @Test fun rememberOff_showsTheTurnedOffState() = runTest(dispatcher) {
        settings.value = AppSettings(rememberRecents = false)
        assertThat(viewModel().state.value.rememberRecents).isFalse()
    }
    @Test fun extractAll_fromTheMenu_opensThenExtracts() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(HomeAction.ExtractAll(item(2, "Photos.zip")))
        val nav = vm.state.value.navigate as HomeNav.ExtractAll
        assertThat(session.request(nav.requestId)!!.extractAll).isTrue()
    }
}
