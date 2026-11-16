package io.github.martindavinci.lunaris.archives.feature.extract

import io.github.martindavinci.lunaris.archives.extract.ConflictChoice
import io.github.martindavinci.lunaris.archives.extract.ExtractionState

/** The extraction screen: the runner's state plus what the dialogs hold. */
data class ExtractUiState(
    val state: ExtractionState = ExtractionState.Idle,
    val choice: ConflictChoice = ConflictChoice.KEEP_BOTH,
    val applyAll: Boolean = true,
    val showApplyAll: Boolean = false,
    val keepBothName: String = "",
    val replaceable: Boolean = true,
    val confirmStop: Boolean = false,
    val showSkipped: Boolean = false,
    val archiveName: String = "",
    val formatLabel: String = "",
    val location: String? = null,
    val pickFolder: Boolean = false,
    val exit: Boolean = false,
)

sealed interface ExtractAction {
    data class PickChoice(val choice: ConflictChoice) : ExtractAction
    data class SetApplyAll(val all: Boolean) : ExtractAction
    data object ContinueConflicts : ExtractAction
    data object CancelConflicts : ExtractAction
    data object RequestStop : ExtractAction
    data object KeepGoing : ExtractAction
    data object Stop : ExtractAction
    data object ContinueLarge : ExtractAction
    data object StopLarge : ExtractAction
    data object ShowSkipped : ExtractAction
    data object HideSkipped : ExtractAction
    data object Done : ExtractAction
    data object ChooseAgain : ExtractAction
    data object PickerHandled : ExtractAction
}
