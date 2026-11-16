package io.github.martindavinci.lunaris.archives.feature.create

import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.create.CreateState

/** The New archive screen: picks, options, and the runner's state. The password itself is never here. */
data class CreateUiState(
    val items: List<CreateItem> = emptyList(),
    val totalBytes: Long = 0,
    val name: String = "",
    val nameError: Boolean = false,
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
)

sealed interface CreateAction {
    data class AddFiles(val uris: List<String>) : CreateAction
    data class AddFolder(val uri: String) : CreateAction
    data class Remove(val item: CreateItem) : CreateAction
    data class SetName(val name: String) : CreateAction
    data class SetLevel(val level: CompressionLevel) : CreateAction
    data class SetProtect(val on: Boolean) : CreateAction
    /** The password itself never reaches the view model before "Create": only what the screen needs to show. */
    data class PasswordChanged(val level: Int, val matches: Boolean) : CreateAction
    class CreateAt(val uri: String, val password: CharArray?) : CreateAction
    data object RequestStop : CreateAction
    data object KeepGoing : CreateAction
    data object Stop : CreateAction
    data object Share : CreateAction
    data object OpenCreated : CreateAction
    data object Navigated : CreateAction
    data object FailureShown : CreateAction
}
