package io.github.martindavinci.lunaris.pdf

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val prepared = mutableListOf<String>()
    private val gate = CompletableDeferred<Unit>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MainViewModel(
        settings = flowOf(AppSettings()),
        openNew = {
            prepared += it
            gate.await()
        },
    ).also { it.state.launchIn(it.viewModelScope) }

    @Test fun fileFromAnotherApp_goesToTheReaderOncePrepared() {
        val vm = viewModel()
        var requests = 0
        vm.readerRequests.onEach { requests++ }.launchIn(vm.viewModelScope)
        vm.openFromIntent("content://a/1")
        assertThat(prepared).containsExactly("content://a/1")
        assertThat(vm.state.value.opening).isTrue()
        assertThat(requests).isEqualTo(0)
        gate.complete(Unit)
        assertThat(vm.state.value.opening).isFalse()
        assertThat(requests).isEqualTo(1)
    }
}
