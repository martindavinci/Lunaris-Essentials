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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import java.io.File
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
    @Test fun archiveGoneWhileOpen_isGone_andNothingIsLeft() = runTest {
        engine.extractFailure = java.io.FileNotFoundException("gone")
        assertThat(copier.copy(archive(), notes, null) {}).isEqualTo(CopyResult.Gone)
        engine.extractFailure = SecurityException("permission released")
        assertThat(copier.copy(archive(), notes, null) {}).isEqualTo(CopyResult.Gone)
        assertThat(temp.root.resolve("open").walk().filter { it.isFile }.toList()).isEmpty()
    }
    @Test fun aFailedCopy_leavesTheFinishedCopyOfTheSameEntry() = runTest {
        val unsized = entry(0, "notes.md", size = null)            // no size: never reused, always copied again
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        val first = copier.copy(archive(), unsized, null) {} as CopyResult.Ready
        script(EngineRoute.LIBARCHIVE, *ended(0, EntryOutcome.DAMAGED))
        assertThat(copier.copy(archive(), unsized, null) {}).isEqualTo(CopyResult.Damaged)
        assertThat(File(first.path).readText()).isEqualTo("hello")
    }
    @Test fun aCancelledCopy_doesNotTakeTheFileOfAnotherCopy() = runTest {
        val reached = Channel<Unit>(Channel.UNLIMITED)
        val gate = CompletableDeferred<Unit>()
        val slow = object : ArchiveEngine by engine {
            override fun extract(source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray, expected: Map<Long, RawPath>) = flow {
                emit(EngineEvent.EntryStarted(0)); emit(EngineEvent.Bytes("he".toByteArray(), 2))
                reached.send(Unit); gate.await()
                emit(EngineEvent.Bytes("llo".toByteArray(), 3)); emit(EngineEvent.EntryFinished(0, EntryOutcome.OK))
                emit(EngineEvent.Finished(EngineStatus.OK))
            }
        }
        val copier = EntryCopier(slow, TempCopies(temp.root), { Long.MAX_VALUE }, { it.path }, { null })
        val first = launch { copier.copy(archive(), notes, null) {} }
        reached.receive()
        val second = async { copier.copy(archive(), notes, null) {} }
        reached.receive()                       // both are in the middle of the same file now
        first.cancelAndJoin()                   // the first one cleans up
        gate.complete(Unit)
        val result = second.await() as CopyResult.Ready
        assertThat(File(result.path).readText()).isEqualTo("hello")
    }
    @Test fun aNameWithNoWritableForm_isCopiedUnderAFallbackName() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        val result = copier.copy(archive(), entry(0, "...", size = 5), null) {} as CopyResult.Ready
        assertThat(result.uri).endsWith("/0-0/file")
    }
    @Test fun theDiskWorkRunsOffTheCallersThread() = runTest {
        val caller = Thread.currentThread()
        var askedOn: Thread? = null
        val copier = EntryCopier(engine, TempCopies(temp.root), { askedOn = Thread.currentThread(); Long.MAX_VALUE }, { it.path }, { null })
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        copier.copy(archive(), notes, null) {}
        assertThat(askedOn).isNotSameInstanceAs(caller)
    }
    @Test fun copyAll_readsTheArchiveOncePerRoute() = runTest {
        val three = listOf(entry(0, "a.md", size = 1), entry(1, "b.md", size = 1), entry(2, "c.md", size = 1))
        script(EngineRoute.LIBARCHIVE, *three.flatMap { e -> ok(e.index, byteArrayOf('x'.code.toByte())).dropLast(1) }.toTypedArray(),
            EngineEvent.Finished(EngineStatus.OK))
        val results = copier.copyAll(archive(), three, null)
        assertThat(engine.extractCalls.map { it.third }).containsExactly(listOf(0L, 1L, 2L))
        assertThat(results.map { File((it as CopyResult.Ready).path).readText() }).containsExactly("x", "x", "x")
    }
    @Test fun copyAll_aDamagedEntry_failsOnlyThatEntry_andReusesWhatIsThere() = runTest {
        script(EngineRoute.LIBARCHIVE, *ok(0, "hello".toByteArray()))
        copier.copy(archive(), notes, null) {}
        val other = entry(1, "b.md", size = 5)
        script(EngineRoute.LIBARCHIVE, *ended(1, EntryOutcome.DAMAGED))
        val results = copier.copyAll(archive(), listOf(notes, other), null)
        assertThat(results[0]).isInstanceOf(CopyResult.Ready::class.java)
        assertThat(results[1]).isEqualTo(CopyResult.Damaged)
        assertThat(engine.extractCalls.last().third).containsExactly(1L)
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
