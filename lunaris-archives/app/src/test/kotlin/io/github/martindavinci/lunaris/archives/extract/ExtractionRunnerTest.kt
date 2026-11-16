package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.safety.RatioGuard
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.FakeTree
import io.github.martindavinci.lunaris.archives.testing.entry
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractionRunnerTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeArchiveEngine()
    private val marks = mutableListOf<String>()
    private val marker = object : ExtractMarker {
        override fun set(token: String) { marks += token }
        override fun clear() { marks += CLEARED }
    }
    private var guard = RatioGuard()
    private val runner = ExtractionRunner(engine, marker, dispatcher) { guard }
    private val entries = index("docs/a.md", "b.txt").entries   // 1 KiB each, indices 0 and 1
    private fun archive(list: List<ArchiveEntry> = entries, kind: ArchiveKind = ArchiveKind.ZIP) = OpenArchive(
        "id", ArchiveSource("content://a", "a.zip", 1, 1, "0123456789abcdef", kind) { error("unused") },
        EngineRoute.LIBARCHIVE, index().copy(kind = kind, entries = list), null, null,
    )
    private fun file(i: Long, bytes: ByteArray = ByteArray(1024) { i.toByte() }, outcome: EntryOutcome = EntryOutcome.OK) = listOf(
        EngineEvent.EntryStarted(i), EngineEvent.Progress(i * 100), EngineEvent.Bytes(bytes, bytes.size),
        EngineEvent.EntryFinished(i, outcome),
    )
    private fun script(vararg files: List<EngineEvent>, status: EngineStatus = EngineStatus.OK, route: EngineRoute = EngineRoute.LIBARCHIVE) {
        engine.extracts[route] = files.flatMap { it } + EngineEvent.Finished(status)
    }
    private fun ExtractionRunner.run(tree: FakeTree, list: List<ArchiveEntry> = entries, password: String? = null) =
        start(ExtractJob(archive(list), list, password?.toCharArray(), tree))
    private val finished get() = runner.state.value as ExtractionState.Finished

    @Test fun plainExtraction_writesEveryFileInFolders_andFinishes() = runTest(dispatcher) {
        val tree = FakeTree(); script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(finished.written).isEqualTo(2)
        assertThat(tree.read("docs", "a.md")).hasLength(1024); assertThat(tree.read("b.txt")).hasLength(1024)
    }
    @Test fun unsafeEntries_areNeverWritten_andAreReportedAsSkipped() = runTest(dispatcher) {
        val list = entries + entry(2, "../x") + entry(3, "link", type = EntryType.SYMLINK)
        val tree = FakeTree(); script(file(0), file(1))
        runner.run(tree, list); advanceUntilIdle()
        assertThat(finished.skipped).containsExactly(SkippedEntry("../x", SkipReason.OUTSIDE_FOLDER), SkippedEntry("link", SkipReason.LINK))
        assertThat(tree.files.values.flatMap { it.keys }).containsExactly("a.md", "b.txt")
    }
    @Test fun conflicts_stopBeforeWriting_untilResolved() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "b.txt")).use { it.write(1) }
        script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        val conflicts = runner.state.value as ExtractionState.Conflicts
        assertThat(tree.read("docs", "a.md")).isNull()
        runner.resolve(conflicts.pending.associate { it.file to ConflictChoice.KEEP_BOTH }); advanceUntilIdle()
        assertThat(tree.read("b (1).txt")).hasLength(1024); assertThat(tree.read("b.txt")).hasLength(1)
    }
    @Test fun cancelConflicts_writesNothing() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "b.txt")).close()
        runner.run(tree); advanceUntilIdle()
        runner.cancelConflicts()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Idle); assertThat(engine.extractCalls).isEmpty()
    }
    @Test fun replace_writesTheNewContent() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "b.txt")).use { it.write(ByteArray(9)) }
        script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        runner.resolve((runner.state.value as ExtractionState.Conflicts).pending.associate { it.file to ConflictChoice.REPLACE }); advanceUntilIdle()
        assertThat(tree.read("b.txt")).hasLength(1024)
    }
    @Test fun replace_whoseEntryFails_keepsTheOriginal() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "b.txt")).use { it.write(ByteArray(9)) }
        script(file(0), file(1, outcome = EntryOutcome.DAMAGED))
        runner.run(tree); advanceUntilIdle()
        runner.resolve((runner.state.value as ExtractionState.Conflicts).pending.associate { it.file to ConflictChoice.REPLACE }); advanceUntilIdle()
        assertThat(tree.read("b.txt")).hasLength(9)
        assertThat(tree.files[emptyList()]!!.keys).containsExactly("b.txt")
    }
    @Test fun replace_stopped_keepsTheOriginal() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "b.txt")).use { it.write(ByteArray(9)) }
        guard = RatioGuard(checkAfterBytes = 1, maxRatio = 1)          // asks inside file 2: a moment to press Stop
        script(file(0, ByteArray(10)), listOf(EngineEvent.EntryStarted(1), EngineEvent.Progress(5), EngineEvent.Bytes(ByteArray(900), 900)))
        runner.run(tree); advanceUntilIdle()
        runner.resolve((runner.state.value as ExtractionState.Conflicts).pending.associate { it.file to ConflictChoice.REPLACE }); advanceUntilIdle()
        runner.answerLarge(true); advanceUntilIdle()
        runner.stop(); advanceUntilIdle()
        assertThat(tree.read("b.txt")).hasLength(9)
        assertThat(tree.files[emptyList()]!!.keys).containsExactly("b.txt")
    }
    @Test fun replace_leavesOneFileUnderTheOriginalName() = runTest(dispatcher) {
        val tree = FakeTree(); tree.open(tree.createFile(tree.ensureFolder(emptyList()), "B.TXT")).use { it.write(ByteArray(9)) }
        script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        runner.resolve((runner.state.value as ExtractionState.Conflicts).pending.associate { it.file to ConflictChoice.REPLACE }); advanceUntilIdle()
        assertThat(tree.files[emptyList()]!!.keys).containsExactly("B.TXT")
        assertThat(tree.read("B.TXT")).hasLength(1024)
    }
    @Test fun providerAppendingAnExtension_doesNotBreakTheRun() = runTest(dispatcher) {
        val tree = FakeTree(appendsExtension = true); script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(finished.written).isEqualTo(finished.total)
    }
    @Test fun notEnoughSpaceUpFront_failsBeforeWriting() = runTest(dispatcher) {
        val tree = FakeTree(free = 10); script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.NO_SPACE, neededBytes = 2048, freeBytes = 10))
        assertThat(tree.files.values.flatMap { it.keys }).isEmpty(); assertThat(engine.extractCalls).isEmpty()
    }
    @Test fun runningOutOfSpace_keepsFinishedFiles_andReportsIt() = runTest(dispatcher) {
        val tree = FakeTree(failWithNoSpaceAfterBytes = 1500); script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.RAN_OUT_OF_SPACE))
        assertThat(tree.read("docs", "a.md")).hasLength(1024); assertThat(tree.read("b.txt")).isNull()
    }
    @Test fun folderGone_failsWithFolderGone() = runTest(dispatcher) {
        val tree = FakeTree(goneAfterWrites = 1); script(file(0), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.FOLDER_GONE))
    }
    @Test fun stop_deletesTheHalfWrittenFile_keepsFinishedOnes() = runTest(dispatcher) {
        guard = RatioGuard(checkAfterBytes = 1, maxRatio = 1)          // asks inside file 2: a moment to press Stop
        val tree = FakeTree(); script(file(0, ByteArray(10)), listOf(EngineEvent.EntryStarted(1), EngineEvent.Progress(5), EngineEvent.Bytes(ByteArray(900), 900)))
        runner.run(tree); advanceUntilIdle()
        runner.answerLarge(true); advanceUntilIdle()
        runner.stop(); advanceUntilIdle()
        assertThat(finished.stoppedEarly).isTrue()
        assertThat(tree.read("b.txt")).isNull()
    }
    @Test fun bomb_asksAndWaits_continueResumes_stopEndsTheEntry() = runTest(dispatcher) {
        guard = RatioGuard(checkAfterBytes = 100, maxRatio = 10)
        val tree = FakeTree(); script(file(0, ByteArray(1024)), file(1, ByteArray(1024)))
        runner.run(tree); advanceUntilIdle()
        assertThat(runner.state.value).isInstanceOf(ExtractionState.AskLarge::class.java)
        runner.answerLarge(true); advanceUntilIdle()
        assertThat(runner.state.value).isInstanceOf(ExtractionState.AskLarge::class.java)
        runner.answerLarge(false); advanceUntilIdle()
        assertThat(finished.written).isEqualTo(1); assertThat(finished.damaged).containsExactly("b.txt")
    }
    @Test fun damagedEntry_isListedAndTheRestContinues() = runTest(dispatcher) {
        val tree = FakeTree(); script(file(0, outcome = EntryOutcome.DAMAGED), file(1))
        runner.run(tree); advanceUntilIdle()
        assertThat(finished.damaged).containsExactly("docs/a.md"); assertThat(tree.read("b.txt")).hasLength(1024)
        assertThat(tree.read("docs", "a.md")).isNull()
    }
    @Test fun wrongPasswordForEncryptedEntries_failsWithWrongPassword() = runTest(dispatcher) {
        val list = index("a.md", "b.md", encrypted = setOf("a.md", "b.md")).entries
        val tree = FakeTree()
        script(file(0, outcome = EntryOutcome.WRONG_PASSWORD), file(1, outcome = EntryOutcome.WRONG_PASSWORD), route = EngineRoute.ZIP4J)
        runner.run(tree, list, password = "x"); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.WRONG_PASSWORD))
        assertThat(engine.extractCalls.single().first).isEqualTo(EngineRoute.ZIP4J)
    }
    @Test fun totalBeyondTheDeclaredSumPlusSlack_stops() = runTest(dispatcher) {
        val huge = entries.map { it.copy(size = 1) }
        val tree = FakeTree()
        script(file(0, ByteArray(1)), listOf(EngineEvent.EntryStarted(1), EngineEvent.Progress(0)) + List(1100) { EngineEvent.Bytes(ByteArray(64 * 1024), 64 * 1024) })
        runner.run(tree, huge); advanceUntilIdle()
        assertThat(finished.damaged).containsExactly("b.txt"); assertThat(tree.read("b.txt")).isNull()
    }
    @Test fun pathMismatch_failsAsReaderChanged() = runTest(dispatcher) {
        script(status = EngineStatus.PATH_MISMATCH)
        runner.run(FakeTree()); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.READER_STOPPED))
    }
    @Test fun engineStopped_failsWithReaderStopped() = runTest(dispatcher) {
        engine.extracts[EngineRoute.LIBARCHIVE] = listOf(EngineEvent.Bytes(ByteArray(1), 1))   // bytes outside an entry
        runner.run(FakeTree()); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.READER_STOPPED))
    }
    @Test fun markerIsSetPerFile_andClearedAtTheEnd() = runTest(dispatcher) {
        script(file(0), file(1))
        runner.run(FakeTree()); advanceUntilIdle()
        assertThat(marks.filter { it != CLEARED }).hasSize(2); assertThat(marks.last()).isEqualTo(CLEARED)
    }

    private companion object { const val CLEARED = "cleared" }
}
