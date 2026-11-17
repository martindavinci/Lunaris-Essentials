package io.github.martindavinci.lunaris.archives.feature.edits

import io.github.martindavinci.lunaris.archives.edits.ChangedArchive
import io.github.martindavinci.lunaris.archives.edits.EditOutcome
import io.github.martindavinci.lunaris.archives.edits.RecoveryOutcome
import io.github.martindavinci.lunaris.archives.edits.UpdateResult

/** The archive asked about in a dialog, by URI and by the name the user knows. */
data class EditsTarget(val archiveUri: String, val archiveName: String, val wrong: Boolean = false)

/** Changed files, what is running, and the one dialog or message shown. No password is ever here. */
data class EditsUiState(
    val archives: List<ChangedArchive> = emptyList(),
    /** The archive being updated or saved. */
    val working: String? = null,
    val confirmIgnore: String? = null,
    val password: EditsTarget? = null,
    /** Updating needs the archive picked again, with the right to write. */
    val pick: EditsTarget? = null,
    val message: EditsMessage? = null,
    /** An archive that changed: the browser showing [EditsReload.from] lists it again from [EditsReload.to]. */
    val reload: EditsReload? = null,
    val expanded: Set<String> = emptySet(),
)

data class EditsReload(val from: String, val to: String)

sealed interface EditsMessage {
    data class Updated(val name: String) : EditsMessage
    data class NotUpdated(val name: String) : EditsMessage
    data class ArchiveChanged(val name: String) : EditsMessage
    data class CannotUpdate(val name: String) : EditsMessage
    data object NoSpace : EditsMessage
    data object DifferentFile : EditsMessage
    data object Saved : EditsMessage
    data object SaveFailed : EditsMessage
    data class Restored(val name: String) : EditsMessage
    data class RestoreFailed(val name: String) : EditsMessage

    companion object {
        /** Null for an update that had finished: nothing went wrong to tell. */
        fun of(outcome: RecoveryOutcome): EditsMessage? = when (outcome) {
            is RecoveryOutcome.Restored -> Restored(outcome.name)
            is RecoveryOutcome.RestoreFailed -> RestoreFailed(outcome.name)
            is RecoveryOutcome.Completed -> null
        }

        /** Null for outcomes that open a dialog instead (password, picking the archive) or need no words (ignore). */
        fun of(outcome: EditOutcome, name: String): EditsMessage? = when (outcome) {
            is EditOutcome.Updated -> Updated(name)
            EditOutcome.Saved -> Saved
            EditOutcome.Ignored -> null
            EditOutcome.Failed -> SaveFailed
            is EditOutcome.Refused -> when (outcome.result) {
                UpdateResult.NoSpace -> NoSpace
                UpdateResult.ArchiveChanged -> ArchiveChanged(name)
                UpdateResult.Unsupported -> CannotUpdate(name)
                is UpdateResult.RestoreFailed -> RestoreFailed(name)
                UpdateResult.NeedsPassword, UpdateResult.WrongPassword, UpdateResult.NotWritable -> null
                else -> NotUpdated(name)
            }
        }
    }
}

sealed interface EditsAction {
    data class Update(val archiveUri: String) : EditsAction
    data class Ignore(val archiveUri: String) : EditsAction
    data object ConfirmIgnore : EditsAction
    data object CancelIgnore : EditsAction
    /** The typed password, wiped once used. */
    class SubmitPassword(val password: CharArray) : EditsAction
    data object CancelPassword : EditsAction
    /** The picker answered; null when dismissed. */
    data class Picked(val archiveUri: String, val pickedUri: String?) : EditsAction
    data object CancelPick : EditsAction
    /** "Save a copy" answered: one document for one file, a folder for several; null when dismissed. */
    data class SaveTo(val archiveUri: String, val target: String?, val isFolder: Boolean, val recordId: String? = null) : EditsAction
    data class ToggleShow(val archiveUri: String) : EditsAction
    data object MessageShown : EditsAction
    data object Reloaded : EditsAction
}
