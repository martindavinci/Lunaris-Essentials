package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.file.FakeFileGateway
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.util.MainDispatcherRule
import java.nio.charset.StandardCharsets

class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gateway = FakeFileGateway()
    private val repo = FileRepository(gateway, EncodingCodec())
    private val vm = EditorViewModel(repo)

    @Test
    fun contentChanged_marksDirty() {
        vm.onContentChanged("hello")
        assertThat(vm.state.value.text).isEqualTo("hello")
        assertThat(vm.state.value.dirty).isTrue()
    }

    @Test
    fun load_populatesStateAndClearsDirty() = runTest {
        val ref = FileRef("content://doc/9")
        gateway.store[ref.uri] = "a\nb".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "a.txt"

        vm.load(ref)
        advanceUntilIdle()

        assertThat(vm.state.value.text).isEqualTo("a\nb")
        assertThat(vm.state.value.title).isEqualTo("a.txt")
        assertThat(vm.state.value.fileRef).isEqualTo(ref)
        assertThat(vm.state.value.dirty).isFalse()
    }

    @Test
    fun save_writesFileAndClearsDirty() = runTest {
        val ref = FileRef("content://doc/10")
        vm.onContentChanged("data")
        vm.save(ref)
        advanceUntilIdle()

        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("data")
        assertThat(vm.state.value.dirty).isFalse()
        assertThat(vm.state.value.fileRef).isEqualTo(ref)
    }

    @Test
    fun newFile_resetsToEmptyUntitled() {
        vm.onContentChanged("scratch")
        vm.newFile()
        assertThat(vm.state.value.text).isEqualTo("")
        assertThat(vm.state.value.title).isEqualTo("untitled")
        assertThat(vm.state.value.fileRef).isNull()
        assertThat(vm.state.value.dirty).isFalse()
    }
}
