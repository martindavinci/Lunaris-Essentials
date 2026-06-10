package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tools.lunaris.texteditor.data.FakeRecentsStore
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.file.FakeFileGateway
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxResolver
import java.nio.charset.StandardCharsets

class DocumentIoTest {
    private val gateway = FakeFileGateway()
    private val repo = FileRepository(gateway, EncodingCodec())
    private val recents = FakeRecentsStore()
    private val resolver = SyntaxResolver(LanguageCatalog(listOf(
        LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
        LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py")),
    )))
    private val io = DocumentIo(repo, recents, resolver)

    @Test fun openTab_buildsTab_autoScope_andRecords() = runTest {
        val ref = FileRef("content://1")
        gateway.store[ref.uri] = "print(1)".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "a.py"
        val tab = io.openTab(ref, "id1", emptyMap())
        assertThat(tab.title).isEqualTo("a.py")
        assertThat(tab.scopeName).isEqualTo("source.python")
        assertThat(tab.savedText).isEqualTo("print(1)")
        assertThat(recents.flow.value.map { it.uri }).contains(ref.uri)
    }

    @Test fun persist_writesBytes_returnsName_records() = runTest {
        val ref = FileRef("content://2")
        gateway.names[ref.uri] = "b.txt"
        val name = io.persist(ref, "data", StandardCharsets.UTF_8, tools.lunaris.texteditor.document.LineEnding.LF, "fallback")
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("data")
        assertThat(name).isEqualTo("b.txt")
        assertThat(recents.flow.value.map { it.uri }).contains(ref.uri)
    }

    @Test fun reload_redecodesWithForcedCharset() = runTest {
        val ref = FileRef("content://3")
        gateway.store[ref.uri] = byteArrayOf(0x61, 0x62)
        val (text, cs, _) = io.reload(ref, StandardCharsets.ISO_8859_1)
        assertThat(text).isEqualTo("ab")
        assertThat(cs).isEqualTo(StandardCharsets.ISO_8859_1)
    }
}
