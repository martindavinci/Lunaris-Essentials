package io.github.martindavinci.lunaris.archives.feature.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.extract.Conflict
import io.github.martindavinci.lunaris.archives.extract.ConflictChoice
import io.github.martindavinci.lunaris.archives.extract.ExtractAgain
import io.github.martindavinci.lunaris.archives.extract.ExtractionRunner
import io.github.martindavinci.lunaris.archives.extract.ExtractionState
import io.github.martindavinci.lunaris.archives.extract.KeepBothNamer
import io.github.martindavinci.lunaris.archives.extract.PlannedFile
import io.github.martindavinci.lunaris.archives.extract.SafTarget
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Frames 10–12: one conflict at a time, then the run, then the result. The run itself lives in the runner. */
@HiltViewModel
class ExtractViewModel @Inject constructor(
    private val runner: ExtractionRunner,
    private val again: ExtractAgain,
) : ViewModel() {
    private val _state = MutableStateFlow(ExtractUiState())
    val state: StateFlow<ExtractUiState> = _state.asStateFlow()

    private var pending: List<Conflict> = emptyList()
    private var current = 0
    private val choices = HashMap<PlannedFile, ConflictChoice>()

    init {
        viewModelScope.launch { runner.state.collect(::onRunner) }
    }

    fun onAction(action: ExtractAction) {
        when (action) {
            is ExtractAction.PickChoice -> _state.update { it.copy(choice = allowed(action.choice, pending.getOrNull(current))) }
            is ExtractAction.SetApplyAll -> _state.update { it.copy(applyAll = action.all) }
            ExtractAction.ContinueConflicts -> continueConflicts()
            ExtractAction.CancelConflicts -> { runner.cancelConflicts(); _state.update { it.copy(exit = true) } }
            ExtractAction.RequestStop -> _state.update { it.copy(confirmStop = true) }
            ExtractAction.KeepGoing -> _state.update { it.copy(confirmStop = false) }
            ExtractAction.Stop -> { runner.stop(); _state.update { it.copy(confirmStop = false) } }
            ExtractAction.ContinueLarge -> runner.answerLarge(true)
            ExtractAction.StopLarge -> runner.answerLarge(false)
            ExtractAction.ShowSkipped -> _state.update { it.copy(showSkipped = true) }
            ExtractAction.HideSkipped -> _state.update { it.copy(showSkipped = false) }
            ExtractAction.Done -> { runner.reset(); _state.update { it.copy(exit = true) } }
            ExtractAction.ChooseAgain -> _state.update { it.copy(pickFolder = true) }
            ExtractAction.PickerHandled -> _state.update { it.copy(pickFolder = false) }
        }
    }

    /** The folder picked after "Choose": the same entries go there. */
    fun onFolderPicked(target: SafTarget) = again.again(target)

    private fun onRunner(state: ExtractionState) {
        val job = runner.currentJob
        _state.update {
            it.copy(
                state = state, archiveName = job?.archive?.source?.name.orEmpty(),
                formatLabel = job?.archive?.index?.formatLabel.orEmpty(), location = job?.target?.label,
            )
        }
        if (state is ExtractionState.Conflicts && state.pending !== pending) {
            pending = state.pending
            current = 0
            choices.clear()
            showConflict()
        }
    }

    private fun continueConflicts() {
        val conflict = pending.getOrNull(current) ?: return
        val choice = _state.value.choice
        if (_state.value.applyAll) {
            pending.drop(current).forEach { choices[it.file] = allowed(choice, it) }
            current = pending.size
        } else {
            choices[conflict.file] = choice
            current++
        }
        if (current >= pending.size) runner.resolve(choices.toMap()) else showConflict()
    }

    private fun showConflict() {
        val conflict = pending.getOrNull(current) ?: return
        val name = conflict.file.name
        _state.update {
            it.copy(
                choice = ConflictChoice.KEEP_BOTH, showApplyAll = pending.size - current > 1, replaceable = conflict.replaceable,
                keepBothName = KeepBothNamer.name(name, setOf(name.lowercase(Locale.ROOT))),
            )
        }
    }

    /** A folder in the way is never replaced. */
    private fun allowed(choice: ConflictChoice, conflict: Conflict?): ConflictChoice =
        if (choice == ConflictChoice.REPLACE && conflict?.replaceable == false) ConflictChoice.KEEP_BOTH else choice
}
