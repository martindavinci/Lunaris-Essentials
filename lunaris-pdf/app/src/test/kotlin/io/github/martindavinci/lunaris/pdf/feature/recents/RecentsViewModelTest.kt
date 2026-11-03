package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsViewModelTest {
    private val one = RecentEntry("content://a/1", "one.pdf", 3, "1.5", 10)
    private val entries = MutableStateFlow(listOf(one))
    private val opened = mutableListOf<String>()
    private var readable = true
    private var pinResult = true
    private val documents = object : DocumentActions {
        var shared: String? = "content://share/one.pdf"
        override suspend fun saveCopy(from: String, to: String) = true
        override suspend fun prepareShare(from: String, name: String) = shared
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RecentsViewModel(
        entries = entries,
        remove = { uri -> entries.value = entries.value.filterNot { it.uri == uri } },
        clear = { entries.value = emptyList() },
        openNew = { opened += it },
        setPinned = { uri, on ->
            pinResult.also { if (it) entries.value = entries.value.map { e -> if (e.uri == uri) e.copy(pinned = on) else e } }
        },
        restore = { entries.value = entries.value + it },
        documents = documents,
        thumbnail = { null },
        openRecent = {
            opened += it.uri
            readable
        },
    ).also { it.state.launchIn(it.viewModelScope) }

    @Test fun entries_areShown() {
        assertThat(viewModel().state.value.entries).containsExactly(one)
    }

    @Test fun pickedFile_opensTheReader() {
        val vm = viewModel()
        vm.onAction(RecentsAction.OpenPicked("content://a/9"))
        assertThat(opened).containsExactly("content://a/9")
        assertThat(vm.state.value.openReader).isTrue()
    }

    @Test fun readerOpened_isConsumed() {
        val vm = viewModel()
        vm.onAction(RecentsAction.OpenPicked("content://a/9"))
        vm.onAction(RecentsAction.ReaderOpened)
        assertThat(vm.state.value.openReader).isFalse()
    }

    @Test fun readableEntry_opensTheReader() {
        val vm = viewModel()
        vm.onAction(RecentsAction.OpenEntry(one))
        assertThat(vm.state.value.openReader).isTrue()
        assertThat(vm.state.value.entryGone).isFalse()
    }

    @Test fun entryThatNoLongerOpens_isRemovedWithAMessage() {
        readable = false
        val vm = viewModel()
        vm.onAction(RecentsAction.OpenEntry(one))
        assertThat(vm.state.value.openReader).isFalse()
        assertThat(vm.state.value.entryGone).isTrue()
        assertThat(vm.state.value.entries).isEmpty()
        vm.onAction(RecentsAction.MessageShown)
        assertThat(vm.state.value.entryGone).isFalse()
    }

    @Test fun clearAll_emptiesTheList() {
        val vm = viewModel()
        vm.onAction(RecentsAction.ClearAll)
        assertThat(vm.state.value.entries).isEmpty()
    }

    @Test fun home_isBuiltFromTheEntries() {
        assertThat(viewModel().state.value.home.resume).isEqualTo(one)
    }

    @Test fun query_filtersTheHome() {
        val vm = viewModel()
        vm.onAction(RecentsAction.OpenSearch)
        vm.onAction(RecentsAction.SetQuery("zzz"))
        assertThat(vm.state.value.home.results).isEmpty()
        vm.onAction(RecentsAction.SetQuery("one"))
        assertThat(vm.state.value.home.results).containsExactly(one)
        vm.onAction(RecentsAction.CloseSearch)
        assertThat(vm.state.value.home.results).isNull()
    }

    @Test fun pin_changesTheEntry() {
        val vm = viewModel()
        vm.onAction(RecentsAction.SetPinned(one, true))
        assertThat(vm.state.value.entries.single().pinned).isTrue()
        assertThat(vm.state.value.notice).isNull()
    }

    @Test fun pinOverTheLimit_saysSo() {
        pinResult = false
        val vm = viewModel()
        vm.onAction(RecentsAction.SetPinned(one, true))
        assertThat(vm.state.value.notice).isEqualTo(RecentsNotice.PinLimit)
    }

    @Test fun removing_canBeUndone() {
        val vm = viewModel()
        vm.onAction(RecentsAction.RemoveEntry(one))
        assertThat(vm.state.value.entries).isEmpty()
        assertThat(vm.state.value.notice).isEqualTo(RecentsNotice.Removed(one))
        vm.onAction(RecentsAction.UndoRemove(one))
        assertThat(vm.state.value.entries).containsExactly(one)
    }

    @Test fun sharing_handsOverACopy() {
        val vm = viewModel()
        vm.onAction(RecentsAction.ShareEntry(one))
        assertThat(vm.state.value.shareUri).isEqualTo("content://share/one.pdf")
        vm.onAction(RecentsAction.ShareStarted)
        assertThat(vm.state.value.shareUri).isNull()
    }

    @Test fun sharingAFileThatIsGone_saysSo() {
        documents.shared = null
        val vm = viewModel()
        vm.onAction(RecentsAction.ShareEntry(one))
        assertThat(vm.state.value.notice).isEqualTo(RecentsNotice.ShareFailed)
    }
}
