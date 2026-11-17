package io.github.martindavinci.lunaris.archives.feature.create

import io.github.martindavinci.lunaris.archives.create.ArchiveFormat
import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.FormatChoice
import io.github.martindavinci.lunaris.archives.create.Packing
import io.github.martindavinci.lunaris.archives.create.archiveFormat
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.create.CreateState

/** The New archive screen: picks, options, and the runner's state. The password itself is never here. */
data class CreateUiState(
    val items: List<CreateItem> = emptyList(),
    val totalBytes: Long = 0,
    val name: String = "",
    val nameError: Boolean = false,
    val formatChoice: FormatChoice = FormatChoice.ZIP,
    val packing: Packing = Packing.NONE,
    val level: CompressionLevel = CompressionLevel.NORMAL,
    val protect: Boolean = false,
    val strength: Int = 0,
    val mismatch: Boolean = false,
    val canCreate: Boolean = false,
    val state: CreateState = CreateState.Idle,
    val confirmStop: Boolean = false,
    val pickedFiles: Boolean = false,
    val pickedFolder: Boolean = false,
    /** The request id of the new archive, for the browser: set by "Open". */
    val openRequest: String? = null,
) {
    val format: ArchiveFormat get() = archiveFormat(formatChoice, packing)
}

sealed interface CreateAction {
    data class AddFiles(val uris: List<String>) : CreateAction
    data class AddFolder(val uri: String) : CreateAction
    data class Remove(val item: CreateItem) : CreateAction
    data class SetName(val name: String) : CreateAction
    data class SetLevel(val level: CompressionLevel) : CreateAction
    /** Any choice but ZIP turns the password off: only ZIP can carry one. */
    data class SetFormat(val choice: FormatChoice) : CreateAction
    data class SetPacking(val packing: Packing) : CreateAction
    data class SetProtect(val on: Boolean) : CreateAction
    /** The password itself never reaches the view model before "Create": only what the screen needs to show. */
    data class PasswordChanged(val level: Int, val matches: Boolean) : CreateAction
    /** "Create ZIP" was pressed: the password waits in the view model while the system asks where to save. */
    class HoldPassword(val password: CharArray?) : CreateAction
    /** The picker answered; null when it was dismissed. */
    data class CreateAt(val uri: String?) : CreateAction
    data object RequestStop : CreateAction
    data object KeepGoing : CreateAction
    data object Stop : CreateAction
    data object Share : CreateAction
    data object OpenCreated : CreateAction
    data object Navigated : CreateAction
    data object FailureShown : CreateAction
}
