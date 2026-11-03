package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Brings the document with its edits from the page to a file. The page sends it a piece at a time,
 * each after the last one is written, because the queue of its messages drops the oldest when full.
 */
internal class ReaderExport(
    private val scope: CoroutineScope,
    private val viewer: ViewerChannel,
    private val editedFiles: EditedDocuments,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    private var running: CompletableDeferred<String?>? = null

    /** A uri of the file, or null if it could not be made. */
    suspend fun run(): String? {
        running?.complete(null)
        val answer = CompletableDeferred<String?>()
        running = answer
        update { it.copy(exportProgress = 0f) }
        editedFiles.begin()
        viewer.send(ViewerCommand.ExportDocument)
        return answer.await()
    }

    fun onChunk(event: ViewerEvent.ExportChunk) {
        if (running == null) return
        val bytes = try {
            Base64.getDecoder().decode(event.data)
        } catch (_: IllegalArgumentException) {
            finish(null)
            return
        }
        scope.launch {
            if (editedFiles.append(event.index, bytes)) {
                update { it.copy(exportProgress = (event.index + 1f) / event.total) }
                viewer.send(ViewerCommand.ExportAck(event.index))
            } else {
                finish(null)
            }
        }
    }

    fun onDone(event: ViewerEvent.ExportDone) {
        if (running == null) return
        scope.launch { finish(editedFiles.finish(event.bytes)) }
    }

    fun fail() = finish(null)

    private fun finish(uri: String?) {
        update { it.copy(exportProgress = null) }
        running?.complete(uri)
        running = null
    }
}
