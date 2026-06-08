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
import tools.lunaris.texteditor.syntax.CharsetCatalog
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxResolver
import tools.lunaris.texteditor.syntax.SyntaxSelection
import tools.lunaris.texteditor.util.MainDispatcherRule
import java.nio.charset.StandardCharsets

class EditorViewModelSyntaxTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val gateway = FakeFileGateway()
    private val repo = FileRepository(gateway, EncodingCodec())
    private val catalog = LanguageCatalog(
        listOf(
            LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
            LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py")),
        )
    )
    private val resolver = SyntaxResolver(catalog)
    private val vm = EditorViewModel(repo, resolver, "sunburst", catalog, ColorSchemeCatalog(emptyList(), ""), CharsetCatalog())

    @Test
    fun load_autoDetectsScopeByExtension() = runTest {
        val ref = FileRef("content://doc/p")
        gateway.store[ref.uri] = "print(1)".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "main.py"
        vm.load(ref)
        advanceUntilIdle()
        assertThat(vm.state.value.scopeName).isEqualTo("source.python")
        assertThat(vm.state.value.syntax).isEqualTo(SyntaxSelection.Auto)
    }

    @Test
    fun setSyntaxExplicit_updatesScope() {
        vm.setSyntax(SyntaxSelection.Explicit("python"))
        assertThat(vm.state.value.scopeName).isEqualTo("source.python")
    }

    @Test
    fun setSyntaxNone_clearsScope() {
        vm.setSyntax(SyntaxSelection.Explicit("python"))
        vm.setSyntax(SyntaxSelection.None)
        assertThat(vm.state.value.scopeName).isNull()
    }

    @Test
    fun setColorScheme_updatesState() {
        vm.setColorScheme("base16-tomorrow-night")
        assertThat(vm.state.value.colorSchemeId).isEqualTo("base16-tomorrow-night")
    }

    @Test
    fun defaultColorScheme_appliedInitially() {
        assertThat(vm.state.value.colorSchemeId).isEqualTo("sunburst")
    }

    @Test
    fun reopenWithEncoding_redecodesSameBytes() = runTest {
        val ref = FileRef("content://doc/e")
        gateway.store[ref.uri] = byteArrayOf(0x61, 0x62)
        gateway.names[ref.uri] = "a.txt"
        vm.load(ref)
        advanceUntilIdle()
        vm.reopenWithEncoding(StandardCharsets.ISO_8859_1)
        advanceUntilIdle()
        assertThat(vm.state.value.charset).isEqualTo(StandardCharsets.ISO_8859_1)
        assertThat(vm.state.value.text).isEqualTo("ab")
    }

    @Test
    fun newFile_resetsSyntaxAndScope() {
        vm.setSyntax(SyntaxSelection.Explicit("python"))
        vm.newFile()
        assertThat(vm.state.value.syntax).isEqualTo(SyntaxSelection.Auto)
        assertThat(vm.state.value.scopeName).isNull()
    }

    @Test
    fun reopenWithEncoding_noFile_isNoOp() = runTest {
        val before = vm.state.value
        vm.reopenWithEncoding(StandardCharsets.UTF_16)
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(before)
    }
}
