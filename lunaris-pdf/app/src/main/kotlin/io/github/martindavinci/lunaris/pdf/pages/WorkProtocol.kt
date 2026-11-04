package io.github.martindavinci.lunaris.pdf.pages

import org.json.JSONException
import org.json.JSONObject

enum class WorkFailure { PROTECTED, DAMAGED, UNREADABLE, MEMORY, WRITE, NO_SPACE }

/** What the app asks of the page that makes the files. */
sealed interface WorkCommand {
    data class Start(val job: String) : WorkCommand

    /** The piece was written: the page may send the next one. */
    data class Ack(val output: Int, val index: Int) : WorkCommand

    /** The file is safe: the page may start the next one. */
    data class AckOutput(val output: Int) : WorkCommand
    data object Cancel : WorkCommand
}

sealed interface WorkEvent {
    data object Ready : WorkEvent

    /** [page] of [pages] of [output] is done; [fraction] is of the whole job. */
    data class Progress(val fraction: Float, val page: Int, val pages: Int, val output: Int) : WorkEvent
    data class Chunk(val output: Int, val index: Int, val total: Int, val data: String) : WorkEvent
    data class OutputDone(val output: Int, val bytes: Long) : WorkEvent
    data object Done : WorkEvent

    /** [source] is the one at fault, if the page can tell. */
    data class Failed(val failure: WorkFailure, val source: Int?) : WorkEvent

    /** Not from the page: what is left when its process dies. */
    data object RendererGone : WorkEvent
}

object WorkProtocol {
    private const val PAGE = "window.lunarisWork"
    private const val MAX_CHUNK_CHARS = 2_000_000
    private val FAILURES = mapOf(
        "protected" to WorkFailure.PROTECTED,
        "damaged" to WorkFailure.DAMAGED,
        "unreadable" to WorkFailure.UNREADABLE,
        "memory" to WorkFailure.MEMORY,
    )

    fun encode(command: WorkCommand): String = when (command) {
        is WorkCommand.Start -> "$PAGE.start(${JSONObject.quote(command.job)});"
        is WorkCommand.Ack -> "$PAGE.ack(${command.output},${command.index});"
        is WorkCommand.AckOutput -> "$PAGE.ackOutput(${command.output});"
        WorkCommand.Cancel -> "$PAGE.cancel();"
    }

    /** Null for anything this app did not ask to hear. */
    fun decode(text: String): WorkEvent? = try {
        event(JSONObject(text))
    } catch (_: JSONException) {
        null
    }

    private fun event(message: JSONObject): WorkEvent? = when (message.getString("type")) {
        "ready" -> WorkEvent.Ready
        "progress" -> WorkEvent.Progress(
            message.getDouble("fraction").toFloat(),
            message.getInt("page"),
            message.getInt("pages"),
            message.getInt("output"),
        )
        "chunk" -> chunk(message)
        "outputDone" -> message.getLong("bytes").takeIf { it >= 0 }?.let { WorkEvent.OutputDone(message.getInt("output"), it) }
        "done" -> WorkEvent.Done
        "failed" -> WorkEvent.Failed(
            FAILURES[message.optString("reason")] ?: WorkFailure.DAMAGED,
            if (message.has("source")) message.getInt("source") else null,
        )
        else -> null
    }

    private fun chunk(message: JSONObject): WorkEvent? {
        val index = message.getInt("index")
        val total = message.getInt("total")
        val data = message.getString("data")
        return WorkEvent.Chunk(message.getInt("output"), index, total, data).takeIf { index in 0 until total && data.length <= MAX_CHUNK_CHARS }
    }
}
