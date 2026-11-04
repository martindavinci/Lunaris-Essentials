package io.github.martindavinci.lunaris.pdf.pages

import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The two directions of the conversation with the page that makes the files. */
interface WorkChannel {
    val events: Flow<WorkEvent>
    fun send(command: WorkCommand)
    fun report(event: WorkEvent)
}

sealed interface WorkResult {
    data object Done : WorkResult
    data object Cancelled : WorkResult

    /** The two numbers are there only when both are known. */
    data class Failed(
        val failure: WorkFailure,
        val source: Int?,
        val neededBytes: Long? = null,
        val freeBytes: Long? = null,
    ) : WorkResult
}

/**
 * Runs one job at a time. Each file comes a piece at a time, each piece after the last is written, since
 * the queue of messages from the page drops the oldest when full. Nothing arrives after the job has ended.
 */
class WorkRunner(
    private val channel: WorkChannel,
    private val files: EditedDocuments,
    private val freeBytes: () -> Long,
) {
    private var running: CompletableDeferred<WorkResult>? = null
    private var neededBytes = 0L

    suspend fun run(
        job: PageJob,
        onProgress: (WorkEvent.Progress) -> Unit,
        saveOutput: suspend (Int, String) -> Boolean,
    ): WorkResult = coroutineScope {
        val answer = CompletableDeferred<WorkResult>()
        running = answer
        val listening = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.events.collect { event ->
                if (running === answer) handle(event, job, answer, onProgress, saveOutput)
            }
        }
        val result = answer.await()
        listening.cancel()
        files.clear()
        result
    }

    fun cancel() {
        val current = running ?: return
        running = null
        channel.send(WorkCommand.Cancel)
        current.complete(WorkResult.Cancelled)
    }

    private suspend fun handle(
        event: WorkEvent,
        job: PageJob,
        answer: CompletableDeferred<WorkResult>,
        onProgress: (WorkEvent.Progress) -> Unit,
        saveOutput: suspend (Int, String) -> Boolean,
    ) {
        when (event) {
            WorkEvent.Ready -> channel.send(WorkCommand.Start(job.encode()))
            is WorkEvent.Progress -> onProgress(event)
            is WorkEvent.Chunk -> if (write(event)) channel.send(WorkCommand.Ack(event.output, event.index)) else end(answer, writeFailure())
            is WorkEvent.OutputDone -> {
                val made = files.finish(event.bytes)
                val saved = made != null && saveOutput(event.output, made)
                if (running !== answer) return
                if (saved) channel.send(WorkCommand.AckOutput(event.output)) else end(answer, writeFailure())
            }
            WorkEvent.Done -> finish(answer, WorkResult.Done)
            is WorkEvent.Failed -> finish(answer, WorkResult.Failed(event.failure, event.source))
            WorkEvent.RendererGone -> finish(answer, WorkResult.Failed(WorkFailure.MEMORY, null))
        }
    }

    private suspend fun write(chunk: WorkEvent.Chunk): Boolean {
        val bytes = try {
            Base64.getDecoder().decode(chunk.data)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (chunk.index == 0) {
            files.begin()
            neededBytes = chunk.total * CHUNK_BYTES
        }
        return files.append(chunk.index, bytes)
    }

    private fun writeFailure(): WorkResult.Failed {
        val free = freeBytes()
        return if (free < neededBytes) {
            WorkResult.Failed(WorkFailure.NO_SPACE, null, neededBytes, free)
        } else {
            WorkResult.Failed(WorkFailure.WRITE, null)
        }
    }

    /** The page is still at work, and must stop too. */
    private fun end(answer: CompletableDeferred<WorkResult>, result: WorkResult) {
        channel.send(WorkCommand.Cancel)
        finish(answer, result)
    }

    private fun finish(answer: CompletableDeferred<WorkResult>, result: WorkResult) {
        running = null
        answer.complete(result)
    }

    companion object {
        const val CHUNK_BYTES = 1_048_576L
    }
}
