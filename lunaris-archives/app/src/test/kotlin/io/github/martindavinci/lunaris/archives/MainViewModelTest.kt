package io.github.martindavinci.lunaris.archives

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val settings = MutableStateFlow(AppSettings())
    private val requests = mutableListOf<OpenRequest>()
    private val inbox = mutableListOf<String>()
    private var archive = false
    private fun viewModel() = MainViewModel(settings, { requests += it; "id${requests.size}" }, { inbox += it }, { archive })

    @Test fun viewOfAContentUri_opensTheBrowser() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(VIEW, "content://a/1.zip", emptyList(), fresh = true)
        assertThat(vm.state.value.target).isEqualTo(StartTarget.Browser("id1"))
        assertThat(requests.single().uri).isEqualTo("content://a/1.zip")
    }
    @Test fun sendOfOneArchive_opensTheBrowser() = runTest(dispatcher) {
        archive = true
        val vm = viewModel()
        vm.handle(SEND, null, listOf("content://a/2.7z"), fresh = true)
        assertThat(vm.state.value.target).isEqualTo(StartTarget.Browser("id1"))
    }
    @Test fun sendOfImages_goesToCreate_withTheInboxFilled() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(SEND_MULTIPLE, null, listOf("content://p/1.jpg", "content://p/2.jpg"), fresh = true)
        assertThat(vm.state.value.target).isEqualTo(StartTarget.Create)
        assertThat(inbox).containsExactly("content://p/1.jpg", "content://p/2.jpg").inOrder()
        assertThat(requests).isEmpty()
    }
    @Test fun sendOfTextOnly_saysSo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(SEND, null, emptyList(), fresh = true)
        assertThat(vm.state.value.textOnly).isTrue()
        assertThat(vm.state.value.target).isNull()
    }
    @Test fun recreatedActivity_startsNothingNew() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(VIEW, "content://a/1.zip", emptyList(), fresh = false)
        assertThat(vm.state.value.target).isNull(); assertThat(requests).isEmpty()
    }
    @Test fun targetShown_clearsIt() = runTest(dispatcher) {
        val vm = viewModel()
        vm.handle(VIEW, "content://a/1.zip", emptyList(), fresh = true)
        vm.targetShown()
        assertThat(vm.state.value.target).isNull()
    }
    @Test fun settingsFlowDrivesTheState() = runTest(dispatcher) {
        val vm = viewModel()
        settings.value = AppSettings(theme = ThemeMode.LIGHT)
        assertThat(vm.state.value.settings?.theme).isEqualTo(ThemeMode.LIGHT)
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
        const val SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    }
}
