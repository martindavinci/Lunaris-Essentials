package io.github.martindavinci.lunaris.archives.feature.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.engine.EngineEvent
import io.github.martindavinci.lunaris.archives.engine.EngineRoute
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EntryOutcome
import io.github.martindavinci.lunaris.archives.extract.ConflictChoice
import io.github.martindavinci.lunaris.archives.extract.ExtractFailure
import io.github.martindavinci.lunaris.archives.extract.ExtractJob
import io.github.martindavinci.lunaris.archives.extract.ExtractMarker
import io.github.martindavinci.lunaris.archives.extract.ExtractionRunner
import io.github.martindavinci.lunaris.archives.extract.ExtractionState
import io.github.martindavinci.lunaris.archives.safety.RatioGuard
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.testing.FakeArchiveEngine
import io.github.martindavinci.lunaris.archives.testing.FakeTree
import io.github.martindavinci.lunaris.archives.testing.MainDispatcherRule
import io.github.martindavinci.lunaris.archives.testing.index
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val engine = FakeArchiveEngine()
    private val marker = object : ExtractMarker { override fun set(token: String) = Unit; override fun clear() = Unit }
    private val runner = ExtractionRunner(engine, marker, dispatcher) { RatioGuard() }
    private val names = listOf("a.md", "b.md", "c.md")
    private val archive = OpenArchive(
        "id", ArchiveSource("content://a", "notes.zip", 1, 1, "0123456789abcdef", ArchiveKind.ZIP) { error("unused") },
        EngineRoute.LIBARCHIVE, index(*names.toTypedArray()), null, null,
    )

    private fun tree(vararg existing: String) = FakeTree().also { t ->
        val root = t.ensureFolder(emptyList())
        existing.forEach { t.open(t.createFile(root, it)).close() }
    }
    private fun ok(i: Long) = listOf(EngineEvent.EntryStarted(i), EngineEvent.Bytes(ByteArray(3), 3), EngineEvent.EntryFinished(i, EntryOutcome.OK))
    private fun script() { engine.extracts[EngineRoute.LIBARCHIVE] = names.indices.flatMap { ok(it.toLong()) } + EngineEvent.Finished(EngineStatus.OK) }
    private fun start(target: FakeTree): ExtractViewModel {
        script()
        runner.start(ExtractJob(archive, archive.index.entries, null, target))
        return ExtractViewModel(runner) {}
    }

    @Test fun conflictsShowOneAtATime_withKeepBothSelectedAndApplyAllChecked() = runTest(dispatcher) {
        val vm = start(tree("a.md", "b.md")); advanceUntilIdle()
        val s = vm.state.value
        assertThat(s.state).isInstanceOf(ExtractionState.Conflicts::class.java)
        assertThat(s.choice).isEqualTo(ConflictChoice.KEEP_BOTH); assertThat(s.applyAll).isTrue(); assertThat(s.showApplyAll).isTrue()
        assertThat(s.keepBothName).isEqualTo("a (1).md")
    }
    @Test fun applyAll_resolvesEveryConflictWithTheSameChoice() = runTest(dispatcher) {
        val target = tree("a.md", "b.md")
        val vm = start(target); advanceUntilIdle()
        vm.onAction(ExtractAction.PickChoice(ConflictChoice.SKIP)); vm.onAction(ExtractAction.ContinueConflicts); advanceUntilIdle()
        assertThat((runner.state.value as ExtractionState.Finished).written).isEqualTo(1)
    }
    @Test fun withoutApplyAll_theNextConflictIsAsked() = runTest(dispatcher) {
        val vm = start(tree("a.md", "b.md")); advanceUntilIdle()
        vm.onAction(ExtractAction.SetApplyAll(false)); vm.onAction(ExtractAction.PickChoice(ConflictChoice.SKIP)); vm.onAction(ExtractAction.ContinueConflicts)
        advanceUntilIdle()
        assertThat(vm.state.value.keepBothName).isEqualTo("b (1).md")
        vm.onAction(ExtractAction.ContinueConflicts); advanceUntilIdle()
        assertThat((runner.state.value as ExtractionState.Finished).written).isEqualTo(2)
    }
    @Test fun singleConflict_hidesApplyAll() = runTest(dispatcher) {
        val vm = start(tree("a.md")); advanceUntilIdle()
        assertThat(vm.state.value.showApplyAll).isFalse()
    }
    @Test fun backWhileRunning_asksToStop_keepGoingDismisses_stopStops() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val slow = object : io.github.martindavinci.lunaris.archives.engine.ArchiveEngine by engine {
            override fun extract(source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray, expected: Map<Long, io.github.martindavinci.lunaris.archives.archive.RawPath>) =
                flow { emit(EngineEvent.EntryStarted(0)); gate.await(); emit(EngineEvent.Finished(EngineStatus.OK)) }
        }
        val slowRunner = ExtractionRunner(slow, marker, dispatcher) { RatioGuard() }
        val slowVm = ExtractViewModel(slowRunner) {}
        slowRunner.start(ExtractJob(archive, archive.index.entries, null, tree())); advanceUntilIdle()
        slowVm.onAction(ExtractAction.RequestStop); assertThat(slowVm.state.value.confirmStop).isTrue()
        slowVm.onAction(ExtractAction.KeepGoing); assertThat(slowVm.state.value.confirmStop).isFalse()
        slowVm.onAction(ExtractAction.RequestStop); slowVm.onAction(ExtractAction.Stop); advanceUntilIdle()
        assertThat((slowRunner.state.value as ExtractionState.Finished).stoppedEarly).isTrue()
    }
    @Test fun doneResetsTheRunnerAndLeaves() = runTest(dispatcher) {
        val vm = start(tree()); advanceUntilIdle()
        vm.onAction(ExtractAction.Done); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Idle); assertThat(vm.state.value.exit).isTrue()
    }
    @Test fun chooseAgainAfterFolderGone_requestsThePicker() = runTest(dispatcher) {
        val vm = start(FakeTree(goneAfterWrites = 0)); advanceUntilIdle()
        assertThat(runner.state.value).isEqualTo(ExtractionState.Failed(ExtractFailure.FOLDER_GONE))
        vm.onAction(ExtractAction.ChooseAgain)
        assertThat(vm.state.value.pickFolder).isTrue()
    }
}
