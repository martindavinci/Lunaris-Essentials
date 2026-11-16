package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.entry
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EntryCopierTest {
    @get:Rule val temp = TemporaryFolder()
    private val engine = FakeArchiveEngine()
    private var free = Long.MAX_VALUE
    private val copier by lazy {
        EntryCopier(engine, TempCopies(temp.root), { free }, { file -> "content://test${file.path.replace('\\', '/').substringAfter(temp.root.path.replace('\\', '/'))}" }, { null })
    }
    private val notes = entry(0, "notes.md", size = 5)
    private val secret = entry(1, "secret.md", size = 5, encrypted = true)

    private fun archive(kind: ArchiveKind = ArchiveKind.ZIP) = OpenArchive(
        "id", ArchiveSource("content://a/1", "a.zip", 10, 1, "0123456789abcdef", kind) { error("unused") },
        EngineRoute.LIBARCHIVE, index("notes.md", kind = kind), null, null,
    )
    private fun script(route: EngineRoute, vararg events: EngineEvent) { engine.extracts[route] = events.toList() }
    private fun ok(index: Long, bytes: ByteArray) = arrayOf(
        EngineEvent.EntryStarted(index), EngineEvent.Bytes(bytes, bytes.size), EngineEvent.Progress(9),
        EngineEvent.EntryFinished(index, EntryOutcome.OK), EngineEvent.Finished(EngineStatus.OK),
    )
    private fun ended(index: Long, outcome: EntryOutcome) =
        arrayOf(EngineEvent.EntryStarted(index), EngineEvent.EntryFinished(index, outcome), EngineEvent.Finished(EngineStatus.OK))

    @Test fun copiesOneEntryAndReturnsAContentUriWithTheMime() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        val result = copier.copy(archive(), notes, null) {} as CopyResult.Ready
        assertThat(result.uri).matches("content://test/open/0123456789abcdef/0-0/notes.md")
        assertThat(result.mime).isEqualTo("text/markdown")
    }
    @Test fun reusesAnExistingCopyWithoutTheEngine() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        copier.copy(archive(), notes, null) {}
        copier.copy(archive(), notes, null) {}
        assertThat(engine.extractCalls).hasSize(1)
    }
    @Test fun wrongPasswordOutcome_isWrongPassword_andTheFileIsDeleted() = runTest {
        script(EngineRoute.ZIP4J, *ended(1, EntryOutcome.WRONG_PASSWORD))
        assertThat(copier.copy(archive(), secret, "x".toCharArray()) {}).isEqualTo(CopyResult.WrongPassword)
        assertThat(temp.root.resolve("open").walk().filter { it.isFile }.toList()).isEmpty()
    }
    @Test fun encryptedWithoutPassword_isNeedsPassword() = runTest {
        assertThat(copier.copy(archive(), secret, null) {}).isEqualTo(CopyResult.NeedsPassword)
        assertThat(engine.extractCalls).isEmpty()
    }
    @Test fun pathMismatch_isChanged() = runTest {
        script(EngineRoute.LIBARCHIVE, EngineEvent.Finished(EngineStatus.PATH_MISMATCH))
        assertThat(copier.copy(archive(), notes, null) {}).isEqualTo(CopyResult.Changed)
    }
    @Test fun moreBytesThanDeclared_isDamaged() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, ByteArray(5 + Limits.OVERRUN_SLACK_BYTES.toInt() + 1)))
        assertThat(copier.copy(archive(), notes, null) {}).isEqualTo(CopyResult.Damaged)
        assertThat(temp.root.resolve("open").walk().filter { it.isFile }.toList()).isEmpty()
    }
    @Test fun notEnoughSpace_isNoSpace_beforeCallingTheEngine() = runTest {
        free = 0
        assertThat(copier.copy(archive(), notes, null) {}).isEqualTo(CopyResult.NoSpace)
        assertThat(engine.extractCalls).isEmpty()
    }
    @Test fun cancellation_deletesThePartialFile() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        lateinit var job: Job
        val running = async { copier.copy(archive(), notes, null) { job.cancel() } }
        job = running
        assertThat(runCatching { running.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(temp.root.resolve("open").walk().filter { it.isFile }.toList()).isEmpty()
    }
    @Test fun rar4EncryptedEntry_isRar4Password() = runTest {
        assertThat(copier.copy(archive(ArchiveKind.RAR4), secret, "x".toCharArray()) {}).isEqualTo(CopyResult.Rar4Password)
        assertThat(engine.extractCalls).isEmpty()
    }
    @Test fun routeForEncryptedZipEntry_isZip4j() = runTest {
        script(EngineRoute.ZIP4J, *ok(1, "hello".toByteArray())); script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        copier.copy(archive(), secret, "pw".toCharArray()) {}
        copier.copy(archive(), notes, null) {}
        assertThat(engine.extractCalls.map { it.first }).containsExactly(EngineRoute.ZIP4J, EngineRoute.LIBARCHIVE).inOrder()
        assertThat(engine.extractCalls.first().second).isEqualTo("pw")
    }
}
