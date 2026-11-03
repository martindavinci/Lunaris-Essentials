package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationHints
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationPalette
import io.github.martindavinci.lunaris.pdf.annotate.EditedDocuments
import io.github.martindavinci.lunaris.pdf.annotate.SignatureLibrary
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import kotlinx.coroutines.CoroutineScope
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
    private var waitingFile: (suspend (String) -> Unit)? = null
    private val export = ReaderExport(scope, viewer, editedFiles, update)
    private val marks = ReaderMarks(scope, viewer, signatures, { state().annotation }, ::changeAnnotation)
    private val tools = ReaderTools(scope, viewer, hints, marks, state, update, ::changeAnnotation)

    init {
        scope.launch { editedFiles.clear() }
    }

    fun onAction(action: EditAction) {
        when (action) {
            EditAction.StartAnnotating -> tools.start()
            is EditAction.ChooseTool -> tools.choose(action.tool)
            is EditAction.ChooseColor -> tools.chooseColor(action.color)
            is EditAction.ChoosePenThickness -> tools.changeOptions { it.copy(penThickness = action.thickness) }
            is EditAction.SetHighlightFree -> tools.changeOptions { it.copy(highlightFree = action.on) }
            is EditAction.ChangeTextSize -> tools.changeOptions { it.copy(textSize = AnnotationPalette.textSize(it.textSize, action.steps)) }
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
            is EditAction.SaveNote -> marks.saveNote(action.id, action.text)
            EditAction.DismissNote -> marks.dismissNote()
            is EditAction.AddSignature -> marks.addSignature(action.png)
            is EditAction.RemoveSignature -> marks.removeSignature(action.id)
            is EditAction.ChooseSignature -> marks.chooseSignature(action.id)
            EditAction.CloseSignatures -> changeAnnotation { it.copy(signatures = null) }
            EditAction.Print -> withCurrentFile { uri -> update { it.copy(printUri = uri) } }
            EditAction.PrintStarted -> update { it.copy(printUri = null) }
            EditAction.ConfirmSigned -> {
                update { it.copy(signedWarning = false) }
                sendWaitingFile(edited = true)
            }
            EditAction.CancelSigned -> {
                update { it.copy(signedWarning = false) }
                waitingFile = null
            }
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
        val lost = state().hasUnsavedEdits
        waitingFile = null
        update { it.copy(annotation = null, edits = DocumentEdits(), saveSheet = null, signedWarning = false) }
        export.fail()
        if (lost) update { it.copy(notice = ReaderNotice.EDITS_LOST) }
    }

    /** Calls [action] with a uri of the document as it stands, with its edits if it has any. */
    fun withCurrentFile(action: suspend (String) -> Unit) {
        waitingFile = action
        // With changes the page goes first: a digital signature is about to break, and that gets said.
        if (state().hasUnsavedEdits) viewer.send(ViewerCommand.SettleEdits) else sendWaitingFile(edited = false)
    }

    private fun sendWaitingFile(edited: Boolean) {
        val action = waitingFile ?: return
        waitingFile = null
        scope.launch {
            val uri = if (edited) export.run() else state().uri
            if (uri != null) action(uri) else if (state().hasUnsavedEdits) notifySaveFailed()
            export.done()
        }
    }

    private fun notifySaveFailed() = update { it.copy(notice = ReaderNotice.SAVE_FAILED) }

    private fun settle(leave: Boolean) {
        leaveAfter = leave
        viewer.send(ViewerCommand.SettleEdits)
    }

    private fun onSettled(event: ViewerEvent.EditsSettled) {
        if (waitingFile != null) {
            if (event.changes > 0 && event.signed) {
                update { it.copy(signedWarning = true) }
            } else {
                sendWaitingFile(edited = event.changes > 0)
            }
        } else if (event.changes > 0) {
            update { it.copy(saveSheet = SaveSheet(event.changes, event.pages, event.signed, leaveAfter)) }
        } else {
            endEditing()
            if (leaveAfter) update { it.copy(closeReader = true) }
        }
    }

    private fun keepEditing() {
        update { it.copy(saveSheet = null) }
        changeAnnotation { it.copy(placingSignature = false) }
        if (state().annotation != null) tools.send()
    }

    private fun endEditing() {
        update { it.copy(annotation = null, edits = DocumentEdits(), saveSheet = null) }
        tools.send()
    }

    private fun discard() {
        val leave = state().saveSheet?.leaveAfter == true
        endEditing()
        if (leave) update { it.copy(closeReader = true) } else reload()
    }

    private fun reload() = update { it.copy(loading = true, viewRevision = it.viewRevision + 1) }

    /** The question was answered, but the page with the changes may have gone while the system picker was open. */
    private fun save(target: String, reload: Boolean) {
        val sheet = state().saveSheet ?: return
        val leave = sheet.leaveAfter
        scope.launch {
            val saved = export.run()?.let { documents.saveCopy(it, target) } == true
            export.done()
            if (!saved) {
                if (state().hasUnsavedEdits) notifySaveFailed()
                return@launch
            }
            editedFiles.clear()
            viewer.send(ViewerCommand.MarkSaved)
            endEditing()
            update { it.copy(notice = ReaderNotice.SAVED, closeReader = leave) }
            if (reload && !leave) reload()
        }
    }

    private fun changeAnnotation(change: (AnnotationUiState) -> AnnotationUiState) = update { current ->
        current.copy(annotation = current.annotation?.let(change))
    }
}
