package io.github.martindavinci.lunaris.pdf.pages

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkRunnerTest {
    private class FakeChannel : WorkChannel {
        val flow = MutableSharedFlow<WorkEvent>(extraBufferCapacity = 64)
        val sent = mutableListOf<WorkCommand>()
        override val events get() = flow
        override fun send(command: WorkCommand) {
            sent += command
        }
        override fun report(event: WorkEvent) {
            flow.tryEmit(event)
        }
    }

    private class FakeFiles(var failWrites: Boolean = false) : EditedDocuments {
        val pieces = mutableListOf<Int>()
        var begun = 0
        var cleared = 0
        override suspend fun begin() {
            begun++
            pieces.clear()
        }
        override suspend fun append(index: Int, bytes: ByteArray): Boolean {
            if (failWrites) return false
            pieces += index
            return true
        }
        override suspend fun finish(size: Long): String? = "file://made$begun"
        override suspend fun clear() {
            cleared++
        }
    }

    private val data = Base64.getEncoder().encodeToString(byteArrayOf(1))
    private val job = PageJob(listOf(SourceKind.PDF), listOf(listOf(PageSlot(0, SlotContent.PageOf(0, 0)))))
    private val channel = FakeChannel()
    private val files = FakeFiles()
    private val saved = mutableListOf<Pair<Int, String>>()
    private val runner = WorkRunner(channel, files, freeBytes = { FREE })

    private fun TestScope.start(save: Boolean = true) = async(UnconfinedTestDispatcher(testScheduler)) {
        runner.run(job, onProgress = {}) { output, uri ->
            saved += output to uri
            save
        }
    }

    @Test
    fun `the job goes out only when the page is ready`() = runTest {
        val result = start()
        assertThat(channel.sent).isEmpty()
        channel.report(WorkEvent.Ready)
        assertThat(channel.sent).containsExactly(WorkCommand.Start(job.encode()))
        channel.report(WorkEvent.Done)
        assertThat(result.await()).isEqualTo(WorkResult.Done)
    }

    @Test
    fun `pieces in order are written and acknowledged, then saved`() = runTest {
        val result = start()
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 2, data))
        channel.report(WorkEvent.Chunk(0, 1, 2, data))
        channel.report(WorkEvent.OutputDone(0, 2))
        assertThat(files.pieces).containsExactly(0, 1).inOrder()
        assertThat(channel.sent).containsAtLeast(WorkCommand.Ack(0, 0), WorkCommand.Ack(0, 1), WorkCommand.AckOutput(0)).inOrder()
        assertThat(saved).containsExactly(0 to "file://made1")
        channel.report(WorkEvent.Done)
        assertThat(result.await()).isEqualTo(WorkResult.Done)
        assertThat(files.cleared).isGreaterThan(0)
    }

    @Test
    fun `two outputs both reach the save, each from a new file`() = runTest {
        val result = start()
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 1, data))
        channel.report(WorkEvent.OutputDone(0, 1))
        channel.report(WorkEvent.Chunk(1, 0, 1, data))
        channel.report(WorkEvent.OutputDone(1, 1))
        channel.report(WorkEvent.Done)
        result.await()
        assertThat(saved).containsExactly(0 to "file://made1", 1 to "file://made2").inOrder()
    }

    @Test
    fun `progress reaches the caller`() = runTest {
        val seen = mutableListOf<WorkEvent.Progress>()
        val result = async(UnconfinedTestDispatcher(testScheduler)) { runner.run(job, onProgress = { seen += it }) { _, _ -> true } }
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Progress(0.25f, 1, 4, 0))
        channel.report(WorkEvent.Done)
        result.await()
        assertThat(seen).containsExactly(WorkEvent.Progress(0.25f, 1, 4, 0))
    }

    @Test
    fun `a failure of the page names the source`() = runTest {
        val result = start()
        channel.report(WorkEvent.Failed(WorkFailure.PROTECTED, 1))
        assertThat(result.await()).isEqualTo(WorkResult.Failed(WorkFailure.PROTECTED, 1))
    }

    @Test
    fun `a killed renderer is lack of memory, and what comes after writes nothing`() = runTest {
        val result = start()
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.RendererGone)
        assertThat(result.await()).isEqualTo(WorkResult.Failed(WorkFailure.MEMORY, null))
        channel.report(WorkEvent.Chunk(0, 0, 1, data))
        assertThat(files.pieces).isEmpty()
    }

    @Test
    fun `cancelling stops at once, and a late piece writes nothing`() = runTest {
        val result = start()
        channel.report(WorkEvent.Ready)
        runner.cancel()
        assertThat(result.await()).isEqualTo(WorkResult.Cancelled)
        assertThat(channel.sent).contains(WorkCommand.Cancel)
        channel.report(WorkEvent.Chunk(0, 0, 1, data))
        assertThat(files.pieces).isEmpty()
        assertThat(saved).isEmpty()
    }

    @Test
    fun `a piece that cannot be written ends the job`() = runTest {
        files.failWrites = true
        val result = start()
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 1, data))
        assertThat(result.await()).isEqualTo(WorkResult.Failed(WorkFailure.WRITE, null))
        assertThat(channel.sent).contains(WorkCommand.Cancel)
    }

    @Test
    fun `a write that fails with the disk full says so, with the numbers`() = runTest {
        files.failWrites = true
        val full = WorkRunner(channel, files, freeBytes = { 1024L })
        val result = async(UnconfinedTestDispatcher(testScheduler)) { full.run(job, onProgress = {}) { _, _ -> true } }
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 3, data))
        assertThat(result.await()).isEqualTo(
            WorkResult.Failed(WorkFailure.NO_SPACE, null, neededBytes = 3 * WorkRunner.CHUNK_BYTES, freeBytes = 1024L),
        )
    }

    @Test
    fun `a save that fails ends the job`() = runTest {
        val result = start(save = false)
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 1, data))
        channel.report(WorkEvent.OutputDone(0, 1))
        assertThat(result.await()).isEqualTo(WorkResult.Failed(WorkFailure.WRITE, null))
    }

    @Test
    fun `damaged data is a failed write, not a crash`() = runTest {
        val result = start()
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Chunk(0, 0, 1, "***"))
        assertThat(result.await()).isEqualTo(WorkResult.Failed(WorkFailure.WRITE, null))
    }

    private companion object {
        const val FREE = 1L shl 40
    }
}
