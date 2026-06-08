package tools.lunaris.texteditor.file

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.document.LineEnding
import java.nio.charset.StandardCharsets

class FileRepositoryTest {

    private val gateway = FakeFileGateway()
    private val repo = FileRepository(gateway, EncodingCodec())

    @Test
    fun load_decodesBytesAndReportsName() = runTest {
        val ref = FileRef("content://doc/1")
        gateway.store[ref.uri] = "a\r\nb".toByteArray(StandardCharsets.UTF_8)
        gateway.names[ref.uri] = "notes.txt"

        val loaded = repo.load(ref)

        assertThat(loaded.text).isEqualTo("a\nb")
        assertThat(loaded.lineEnding).isEqualTo(LineEnding.CRLF)
        assertThat(loaded.charset).isEqualTo(StandardCharsets.UTF_8)
        assertThat(loaded.name).isEqualTo("notes.txt")
    }

    @Test
    fun save_encodesWithChosenLineEnding() = runTest {
        val ref = FileRef("content://doc/2")
        repo.save(ref, "x\ny", StandardCharsets.UTF_8, LineEnding.CRLF)
        assertThat(String(gateway.store[ref.uri]!!, StandardCharsets.UTF_8)).isEqualTo("x\r\ny")
    }

    @Test
    fun load_returnsNullName_whenGatewayHasNoName() = runTest {
        val ref = FileRef("content://doc/3")
        gateway.store[ref.uri] = "hi".toByteArray(StandardCharsets.UTF_8)
        // no entry in gateway.names

        val loaded = repo.load(ref)

        assertThat(loaded.name).isNull()
        assertThat(loaded.text).isEqualTo("hi")
    }

    @Test
    fun saveThenLoad_roundTripsTextCharsetAndLineEnding() = runTest {
        val ref = FileRef("content://doc/4")
        repo.save(ref, "alpha\nbeta", StandardCharsets.UTF_8, LineEnding.CRLF)

        val loaded = repo.load(ref)

        assertThat(loaded.text).isEqualTo("alpha\nbeta")
        assertThat(loaded.lineEnding).isEqualTo(LineEnding.CRLF)
        assertThat(loaded.charset).isEqualTo(StandardCharsets.UTF_8)
    }
}
