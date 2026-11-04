package io.github.martindavinci.lunaris.pdf.feature.work

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.pages.PageJob
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SourceKind
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobSessionTest {
    private val page = PageSlot(0, SlotContent.PageOf(0, 0))
    private val job = PageJob(listOf(SourceKind.PDF), listOf(listOf(page, page)))
    private val plan = JobPlan(job, listOf("content://doc"))
    private val channel = FakeWorkChannel()
    private val files = FakeOutputFiles()
    private var state: WorkState? = null
    private var saved = 0
    private val target = JobTarget("content://out/new.pdf", "new.pdf")

    private fun session(scope: CoroutineScope) = JobSession(
        scope,
        WorkRunner(channel, FakeWorkFiles(), freeBytes = { Long.MAX_VALUE }),
        files,
        onSaved = { saved++ },
    ) { state = it }

    @Test
    fun `running shows what is made, then saved describes it and it joins the recents`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        assertThat((state as WorkState.Running).sources).containsExactly("content://doc")
        assertThat((state as WorkState.Running).name).isEqualTo("new.pdf")
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Progress(0.5f, 1, 2, 0))
        assertThat((state as WorkState.Running).fraction).isEqualTo(0.5f)
        assertThat((state as WorkState.Running).page).isEqualTo(1)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        val done = state as WorkState.Saved
        assertThat(done.name).isEqualTo("new.pdf")
        assertThat(done.folder).isEqualTo("Download")
        assertThat(done.pages).isEqualTo(2)
        assertThat(done.bytes).isEqualTo(2048)
        assertThat(done.uri).isEqualTo(target.uri)
        assertThat(files.savedTo).containsExactly(target.uri)
        assertThat(files.remembered).containsExactly(target.uri to 2)
        assertThat(saved).isEqualTo(1)
    }

    @Test
    fun `several files are counted, and do not crowd the recents`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        val two = PageJob(listOf(SourceKind.PDF), listOf(listOf(page), listOf(page)))
        val targets = listOf(JobTarget("o/a.pdf", "a.pdf"), JobTarget("o/b.pdf", "b.pdf"))
        session.start(JobKind.SPLIT, targets) { JobPlan(two, listOf("d")) }
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.makeOutput(1)
        channel.report(WorkEvent.Done)
        assertThat(files.savedTo).containsExactly("o/a.pdf", "o/b.pdf").inOrder()
        assertThat((state as WorkState.Saved).files).isEqualTo(2)
        assertThat(files.remembered).isEmpty()
    }

    @Test
    fun `cancelling ends the state, takes the empty target away and saves nothing`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        channel.report(WorkEvent.Ready)
        session.cancel()
        assertThat(state).isNull()
        assertThat(files.deleted).containsExactly(target.uri)
        assertThat(saved).isEqualTo(0)
    }

    @Test
    fun `a target that held something before is left alone`() = runTest(UnconfinedTestDispatcher()) {
        files.withContent = setOf(target.uri)
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        session.cancel()
        assertThat(files.deleted).isEmpty()
    }

    @Test
    fun `cancelling while preparing stops the preparing too`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        val never = CompletableDeferred<JobPlan?>()
        session.start(JobKind.CREATE, listOf(target)) { never.await() }
        assertThat(state).isInstanceOf(WorkState.Running::class.java)
        session.cancel()
        assertThat(state).isNull()
        assertThat(files.deleted).containsExactly(target.uri)
    }

    @Test
    fun `a failure keeps the target for another try, closing takes it away`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Failed(WorkFailure.MEMORY, null))
        assertThat((state as WorkState.Failed).failure).isEqualTo(WorkFailure.MEMORY)
        assertThat(files.deleted).isEmpty()
        session.closeFailure()
        assertThat(state).isNull()
        assertThat(files.deleted).containsExactly(target.uri)
    }

    @Test
    fun `trying again runs it once more on the same target`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Failed(WorkFailure.WRITE, null))
        session.retry()
        assertThat(state).isInstanceOf(WorkState.Running::class.java)
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        assertThat(state).isInstanceOf(WorkState.Saved::class.java)
        assertThat(files.deleted).isEmpty()
    }

    @Test
    fun `a plan that cannot be made is a failure, with the source at fault`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.CREATE, listOf(target)) { throw JobPlanFailure(WorkFailure.UNREADABLE, 2) }
        val failed = state as WorkState.Failed
        assertThat(failed.failure).isEqualTo(WorkFailure.UNREADABLE)
        assertThat(failed.source).isEqualTo(2)
    }

    @Test
    fun `closing the saved sheet ends the state`() = runTest(UnconfinedTestDispatcher()) {
        val session = session(backgroundScope)
        session.start(JobKind.SAVE, listOf(target)) { plan }
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        session.closeSaved()
        assertThat(state).isNull()
    }
}
