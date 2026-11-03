package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationHints
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationOptions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationPalette
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import io.github.martindavinci.lunaris.pdf.annotate.SignatureLibrary
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Annotating and saving for the Reader: the tools, and the way from the page to a file. */
internal class ReaderEdits(
    private val scope: CoroutineScope,
    private val viewer: ViewerChannel,
    private val editedFiles: EditedDocuments,
    private val documents: DocumentActions,
    private val signatures: SignatureLibrary,
    private val hints: AnnotationHints,
    private val state: () -> ReaderUiState,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    private var leaveAfter = false
    private val export = ReaderExport(scope, viewer, editedFiles, update)

    fun onAction(action: EditAction) {
        when (action) {
            EditAction.StartAnnotating -> start()
            is EditAction.ChooseTool -> chooseTool(action.tool)
            is EditAction.ChooseColor -> chooseColor(action.color)
            is EditAction.ChoosePenThickness -> changeOptions { it.copy(penThickness = action.thickness) }
            is EditAction.SetHighlightFree -> changeOptions { it.copy(highlightFree = action.on) }
            is EditAction.ChangeTextSize -> changeOptions { it.copy(textSize = AnnotationPalette.textSize(it.textSize, action.steps)) }
            EditAction.HideScrollHint -> changeAnnotation { it.copy(showScrollHint = false) }
            EditAction.Undo -> viewer.send(ViewerCommand.UndoEdit)
            EditAction.Redo -> viewer.send(ViewerCommand.RedoEdit)
            EditAction.DeleteSelected -> viewer.send(ViewerCommand.DeleteSelected)
            EditAction.Finish -> settle(leave = false)
            EditAction.RequestLeave -> settle(leave = true)
            EditAction.KeepEditing -> keepEditing()
            EditAction.Discard -> discard()
            is EditAction.SaveTo -> save(action.uri, reload = false)
            EditAction.SaveOver -> if (state().canWrite) state().uri?.let { save(it, reload = true) }
            is EditAction.SaveNote -> saveNote(action.id, action.text)
            EditAction.DismissNote -> dismissNote()
            is EditAction.AddSignature -> scope.launch {
                signatures.add(action.png)
                showSignatures()
            }
            is EditAction.RemoveSignature -> scope.launch {
                signatures.remove(action.id)
                showSignatures()
            }
            is EditAction.ChooseSignature -> chooseSignature(action.id)
            EditAction.CloseSignatures -> changeAnnotation { it.copy(signatures = null) }
            EditAction.Print -> withCurrentFile { uri -> update { it.copy(printUri = uri) } }
            EditAction.PrintStarted -> update { it.copy(printUri = null) }
        }
    }

    fun onEvent(event: ViewerEvent.Editing) {
        when (event) {
            is ViewerEvent.EditsChanged -> update {
                it.copy(edits = DocumentEdits(event.changes, event.pages, event.canUndo, event.canRedo))
            }
            is ViewerEvent.EditsSettled -> onSettled(event)
            is ViewerEvent.NoteRequested -> changeAnnotation { it.copy(note = NoteDraft(event.id, event.text)) }
            ViewerEvent.SignaturePlaced -> changeAnnotation { it.copy(placingSignature = false) }
            ViewerEvent.NoTextHere -> changeAnnotation { it.copy(noTextHints = it.noTextHints + 1) }
            is ViewerEvent.ExportChunk -> export.onChunk(event)
            is ViewerEvent.ExportDone -> export.onDone(event)
            ViewerEvent.ExportFailed -> export.fail()
        }
    }

    /** The edits lived in the page that the system took back. */
    fun onPageLost() {
        export.fail()
        if (state().hasUnsavedEdits) update { it.copy(notice = ReaderNotice.EDITS_LOST) }
        update { it.copy(annotation = null, edits = DocumentEdits(), saveSheet = null) }
    }

    /** Calls [action] with a uri of the document as it stands, with its edits if it has any. */
    fun withCurrentFile(action: suspend (String) -> Unit) {
        scope.launch {
            val uri = if (state().hasUnsavedEdits) export.run() else state().uri
            if (uri != null) action(uri) else update { it.copy(notice = ReaderNotice.SAVE_FAILED) }
        }
    }

    private fun start() {
        update { it.copy(annotation = AnnotationUiState(), chromeVisible = true) }
        sendTool()
    }

    private fun chooseTool(tool: AnnotationTool) {
        changeAnnotation { it.copy(tool = tool, placingSignature = false) }
        sendTool()
        when (tool) {
            AnnotationTool.PEN -> offerScrollHint()
            AnnotationTool.SIGN -> scope.launch { showSignatures() }
            else -> Unit
        }
    }

    private fun offerScrollHint() {
        scope.launch {
            if (hints.shown.first() < SCROLL_HINT_TIMES) {
                hints.countOne()
                changeAnnotation { it.copy(showScrollHint = true) }
            }
        }
    }

    private fun chooseColor(color: String) = changeOptions { options ->
        when (state().annotation?.tool) {
            AnnotationTool.HIGHLIGHT -> options.copy(highlightColor = color)
            AnnotationTool.PEN -> options.copy(penColor = color)
            AnnotationTool.TEXT -> options.copy(textColor = color)
            else -> options
        }
    }

    private fun changeOptions(change: (AnnotationOptions) -> AnnotationOptions) {
        changeAnnotation { it.copy(options = change(it.options)) }
        sendTool()
    }

    private fun sendTool() {
        val annotation = state().annotation
        viewer.send(ViewerCommand.SetTool(annotation?.let { AnnotationPalette.spec(it.tool, it.options) }))
    }

    private fun changeAnnotation(change: (AnnotationUiState) -> AnnotationUiState) = update { current ->
        current.copy(annotation = current.annotation?.let(change))
    }

    private fun settle(leave: Boolean) {
        leaveAfter = leave
        viewer.send(ViewerCommand.SettleEdits)
    }

    private fun onSettled(event: ViewerEvent.EditsSettled) {
        if (event.changes > 0) {
            update { it.copy(saveSheet = SaveSheet(event.changes, event.pages, event.signed, leaveAfter)) }
        } else {
            endEditing()
            if (leaveAfter) update { it.copy(closeReader = true) }
        }
    }

    private fun keepEditing() {
        update { it.copy(saveSheet = null) }
        if (state().annotation != null) sendTool()
    }

    private fun endEditing() {
        update { it.copy(annotation = null, edits = DocumentEdits(), saveSheet = null) }
        sendTool()
    }

    private fun discard() {
        val leave = state().saveSheet?.leaveAfter == true
        endEditing()
        if (leave) update { it.copy(closeReader = true) } else reload()
    }

    private fun reload() = update { it.copy(loading = true, viewRevision = it.viewRevision + 1) }

    private fun save(target: String, reload: Boolean) {
        val leave = state().saveSheet?.leaveAfter == true
        scope.launch {
            val saved = export.run()?.let { documents.saveCopy(it, target) } == true
            if (!saved) {
                update { it.copy(notice = ReaderNotice.SAVE_FAILED) }
                return@launch
            }
            viewer.send(ViewerCommand.MarkSaved)
            endEditing()
            update { it.copy(notice = ReaderNotice.SAVED, closeReader = leave) }
            if (reload && !leave) reload()
        }
    }

    private fun saveNote(id: String, text: String) {
        viewer.send(if (text.isBlank()) ViewerCommand.RemoveNote(id) else ViewerCommand.SetNoteText(id, text))
        changeAnnotation { it.copy(note = null) }
    }

    private fun dismissNote() {
        state().annotation?.note?.takeIf { it.text.isEmpty() }?.let { viewer.send(ViewerCommand.RemoveNote(it.id)) }
        changeAnnotation { it.copy(note = null) }
    }

    private suspend fun showSignatures() {
        val saved = signatures.list()
        changeAnnotation { it.copy(signatures = saved) }
    }

    private fun chooseSignature(id: String) {
        scope.launch {
            val png = signatures.read(id) ?: return@launch
            viewer.send(ViewerCommand.ArmSignature(Base64.getEncoder().encodeToString(png)))
            changeAnnotation { it.copy(signatures = null, placingSignature = true) }
        }
    }

    private companion object {
        const val SCROLL_HINT_TIMES = 3
    }
}
