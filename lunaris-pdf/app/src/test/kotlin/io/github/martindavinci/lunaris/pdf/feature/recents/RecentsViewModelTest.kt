package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
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

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RecentsViewModel(
        entries = entries,
        remove = { uri -> entries.value = entries.value.filterNot { it.uri == uri } },
        clear = { entries.value = emptyList() },
        openNew = { opened += it },
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
}
