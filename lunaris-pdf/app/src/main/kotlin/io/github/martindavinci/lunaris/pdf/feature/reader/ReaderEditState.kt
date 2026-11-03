package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.annotate.AnnotationOptions
import io.github.martindavinci.lunaris.pdf.annotate.AnnotationTool
import io.github.martindavinci.lunaris.pdf.annotate.SavedSignature

/** While the document is being annotated. [signatures] is set while their panel is open. */
data class AnnotationUiState(
    val tool: AnnotationTool = AnnotationTool.HIGHLIGHT,
    val options: AnnotationOptions = AnnotationOptions(),
    val showScrollHint: Boolean = false,
    val signatures: List<SavedSignature>? = null,
    val placingSignature: Boolean = false,
    val note: NoteDraft? = null,
    /** Grows each time the highlighter finds no text, to say so. */
    val noTextHints: Int = 0,
)

data class NoteDraft(val id: String, val text: String)

/** What changed since the last save. Forms count too, annotating or not. */
data class DocumentEdits(
    val changes: Int = 0,
    val pages: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** The question at the end of editing. [leaveAfter]: the reader closes once it is answered. */
data class SaveSheet(val changes: Int, val pages: Int, val signed: Boolean, val leaveAfter: Boolean)

sealed interface EditAction : ReaderAction {
    data object StartAnnotating : EditAction
    data class ChooseTool(val tool: AnnotationTool) : EditAction
    data class ChooseColor(val color: String) : EditAction
    data class ChoosePenThickness(val thickness: Int) : EditAction
    data class SetHighlightFree(val on: Boolean) : EditAction
    data class ChangeTextSize(val steps: Int) : EditAction
    data object HideScrollHint : EditAction
    data object Undo : EditAction
    data object Redo : EditAction
    data object DeleteSelected : EditAction
    data object Finish : EditAction
    data object RequestLeave : EditAction
    data object KeepEditing : EditAction
    data object Discard : EditAction
    data class SaveTo(val uri: String) : EditAction
    data object SaveOver : EditAction
    data class SaveNote(val id: String, val text: String) : EditAction
    data object DismissNote : EditAction
    class AddSignature(val png: ByteArray) : EditAction
    data class RemoveSignature(val id: String) : EditAction
    data class ChooseSignature(val id: String) : EditAction
    data object CloseSignatures : EditAction
    data object Print : EditAction
    data object PrintStarted : EditAction
    data object ConfirmSigned : EditAction
    data object CancelSigned : EditAction
}
