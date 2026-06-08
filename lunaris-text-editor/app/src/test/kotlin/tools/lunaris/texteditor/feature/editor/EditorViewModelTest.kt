package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.file.FakeFileGateway
import tools.lunaris.texteditor.file.FileGateway
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

    @Test
    fun save_incrementsSavedSignal() = runTest {
        val ref = FileRef("content://doc/11")
        vm.onContentChanged("data")
        vm.save(ref)
        advanceUntilIdle()
        assertThat(vm.state.value.savedSignal).isEqualTo(1)
    }

    @Test
    fun load_failure_setsErrorAndClearsLoading() = runTest {
        // No entry in gateway.store -> FakeFileGateway.readBytes throws.
        vm.load(FileRef("content://missing"))
        advanceUntilIdle()
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun save_failure_setsError() = runTest {
        val throwingRepo = FileRepository(
            object : FileGateway {
                override suspend fun readBytes(ref: FileRef): ByteArray =
                    throw java.io.IOException("read fail")
                override suspend fun writeBytes(ref: FileRef, bytes: ByteArray): Unit =
                    throw java.io.IOException("write fail")
                override suspend fun displayName(ref: FileRef): String? = null
            },
            EncodingCodec(),
        )
        val throwingVm = EditorViewModel(throwingRepo)
        throwingVm.onContentChanged("data")
        throwingVm.save(FileRef("content://doc/12"))
        advanceUntilIdle()
        assertThat(throwingVm.state.value.error).isNotNull()
    }

    @Test
    fun edit_afterSave_reMarksDirty() = runTest {
        val ref = FileRef("content://doc/13")
        vm.onContentChanged("data")
        vm.save(ref)
        advanceUntilIdle()
        assertThat(vm.state.value.dirty).isFalse()

        vm.onContentChanged("data changed")
        assertThat(vm.state.value.dirty).isTrue()
    }
}
