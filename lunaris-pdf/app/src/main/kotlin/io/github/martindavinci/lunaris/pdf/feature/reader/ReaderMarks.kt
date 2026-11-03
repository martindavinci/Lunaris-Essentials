package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.annotate.SignatureLibrary
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Notes and signatures, between their panels and the page. */
internal class ReaderMarks(
    private val scope: CoroutineScope,
    private val viewer: ViewerChannel,
    private val signatures: SignatureLibrary,
    private val annotation: () -> AnnotationUiState?,
    private val change: ((AnnotationUiState) -> AnnotationUiState) -> Unit,
) {
    fun saveNote(id: String, text: String) {
        viewer.send(if (text.isBlank()) ViewerCommand.RemoveNote(id) else ViewerCommand.SetNoteText(id, text))
        change { it.copy(note = null) }
    }

    fun dismissNote() {
        annotation()?.note?.takeIf { it.text.isEmpty() }?.let { viewer.send(ViewerCommand.RemoveNote(it.id)) }
        change { it.copy(note = null) }
    }

    fun addSignature(png: ByteArray) {
        scope.launch {
            signatures.add(png)
            showSignatures()
        }
    }

    fun removeSignature(id: String) {
        scope.launch {
            signatures.remove(id)
            showSignatures()
        }
    }

    fun openSignatures() {
        scope.launch { showSignatures() }
    }

    fun chooseSignature(id: String) {
        scope.launch {
            val png = signatures.read(id) ?: return@launch
            viewer.send(ViewerCommand.ArmSignature(Base64.getEncoder().encodeToString(png)))
            change { it.copy(signatures = null, placingSignature = true) }
        }
    }

    private suspend fun showSignatures() {
        val saved = signatures.list()
        change { it.copy(signatures = saved) }
    }
}
