package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tools.lunaris.texteditor.data.FakeRecentsStore
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.file.FakeFileGateway
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.file.FileRepository
import tools.lunaris.texteditor.file.FileTooLargeException
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

    @Test fun openTab_throwsFileTooLarge_whenOverLimit() = runTest {
        val ref = FileRef("content://big")
        gateway.store[ref.uri] = "x".toByteArray(StandardCharsets.UTF_8)
        gateway.sizes[ref.uri] = DocumentIo.MAX_OPEN_BYTES + 1
        gateway.names[ref.uri] = "huge.txt"
        try {
            io.openTab(ref, "id", emptyMap())
            throw AssertionError("expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertThat(e.limitBytes).isEqualTo(DocumentIo.MAX_OPEN_BYTES)
        }
        // Over-limit files are never recorded as recents.
        assertThat(recents.flow.value.map { it.uri }).doesNotContain(ref.uri)
    }

    @Test fun openTab_opensFile_atExactlyLimit() = runTest {
        val ref = FileRef("content://atlimit")
        gateway.store[ref.uri] = "ok".toByteArray(StandardCharsets.UTF_8)
        gateway.sizes[ref.uri] = DocumentIo.MAX_OPEN_BYTES
        gateway.names[ref.uri] = "edge.txt"
        val tab = io.openTab(ref, "id", emptyMap())
        assertThat(tab.title).isEqualTo("edge.txt")
    }

    @Test fun load_boundedRead_throwsWhenStreamExceedsMax() = runTest {
        // Provider reports no size, but the bounded read still caps memory.
        val ref = FileRef("content://unsized")
        gateway.store[ref.uri] = ByteArray(100)
        try {
            repo.load(ref, maxBytes = 10)
            throw AssertionError("expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertThat(e.limitBytes).isEqualTo(10)
        }
    }

    @Test fun persist_writesBytes_returnsName_records() = runTest {
        val ref = FileRef("content://2")
        gateway.names[ref.uri] = "b.txt"
        val saved = io.persist(ref, "data", StandardCharsets.UTF_8, tools.lunaris.texteditor.document.LineEnding.LF, "fallback")
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("data")
        assertThat(saved.name).isEqualTo("b.txt")
        assertThat(saved.charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(recents.flow.value.map { it.uri }).contains(ref.uri)
    }

    @Test fun reload_redecodesWithForcedCharset() = runTest {
        val ref = FileRef("content://3")
        gateway.store[ref.uri] = byteArrayOf(0x61, 0x62)
        val loaded = io.reload(ref, StandardCharsets.ISO_8859_1)
        assertThat(loaded.text).isEqualTo("ab")
        assertThat(loaded.charset).isEqualTo(StandardCharsets.ISO_8859_1)
    }

    @Test fun persist_fallsBackToUtf8_whenTextDoesNotFitCharset() = runTest {
        val ref = FileRef("content://latin1")
        val text = "price: €"
        val saved = io.persist(ref, text, StandardCharsets.ISO_8859_1, tools.lunaris.texteditor.document.LineEnding.LF, "f.txt")
        // The euro sign can't be written as Latin-1: rather than writing '?', save as UTF-8.
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo(text)
        assertThat(saved.charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(saved.charsetChanged).isTrue()
    }
}
