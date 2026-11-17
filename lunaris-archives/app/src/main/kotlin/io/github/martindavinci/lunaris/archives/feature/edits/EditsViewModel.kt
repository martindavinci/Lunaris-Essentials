package io.github.martindavinci.lunaris.archives.feature.edits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.edits.EditActions
import io.github.martindavinci.lunaris.archives.edits.EditOutcome
import io.github.martindavinci.lunaris.archives.edits.EditWatcher
import io.github.martindavinci.lunaris.archives.edits.EditsRunner
import io.github.martindavinci.lunaris.archives.edits.UpdateJournal
import io.github.martindavinci.lunaris.archives.edits.UpdateResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The banner in the browser and the card on Home: one per archive with changed files, and what follows a tap. */
@HiltViewModel
class EditsViewModel @Inject constructor(
    watcher: EditWatcher,
    private val runner: EditsRunner,
    private val actions: EditActions,
    private val journal: UpdateJournal,
) : ViewModel() {
    private val _state = MutableStateFlow(EditsUiState())
    val state: StateFlow<EditsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { watcher.changed.collect { list -> _state.update { it.copy(archives = list) } } }
        viewModelScope.launch { runner.working.collect { w -> _state.update { it.copy(working = w?.archiveUri) } } }
        viewModelScope.launch { runner.result.collect { r -> r?.let { finished(it) } } }
        viewModelScope.launch {
            journal.outcomes.collect { outcomes ->
                outcomes.firstOrNull()?.let { o -> _state.update { it.copy(message = EditsMessage.of(o)) }; journal.shown() }
            }
        }
    }

    fun onAction(action: EditsAction) {
        when (action) {
            is EditsAction.Update -> update(action.archiveUri, null, null)
            is EditsAction.Ignore -> _state.update { it.copy(confirmIgnore = action.archiveUri) }
            EditsAction.ConfirmIgnore -> _state.value.confirmIgnore?.let { uri ->
                _state.update { it.copy(confirmIgnore = null) }
                run(uri) { ignore(uri) }
            }
            EditsAction.CancelIgnore -> _state.update { it.copy(confirmIgnore = null) }
            is EditsAction.SubmitPassword -> {
                val target = _state.value.password
                _state.update { it.copy(password = null) }
                if (target == null) action.password.fill(WIPED) else update(target.archiveUri, action.password, null)
            }
            EditsAction.CancelPassword -> _state.update { it.copy(password = null) }
            is EditsAction.Picked -> picked(action.archiveUri, action.pickedUri)
            EditsAction.CancelPick -> _state.update { it.copy(pick = null) }
            is EditsAction.SaveTo -> action.target?.let { target ->
                run(action.archiveUri) { if (action.isFolder) saveAll(action.archiveUri, target) else saveOne(action.archiveUri, target) }
            }
            is EditsAction.ToggleShow -> _state.update { s ->
                s.copy(expanded = if (action.archiveUri in s.expanded) s.expanded - action.archiveUri else s.expanded + action.archiveUri)
            }
            EditsAction.MessageShown -> _state.update { it.copy(message = null) }
            EditsAction.Reloaded -> _state.update { it.copy(reload = null) }
        }
    }

    private fun update(uri: String, password: CharArray?, writableUri: String?) {
        if (!run(uri) { update(uri, password, writableUri) }) password?.fill(WIPED)
    }

    private fun picked(uri: String, pickedUri: String?) {
        _state.update { it.copy(pick = null) }
        pickedUri ?: return
        viewModelScope.launch {
            if (actions.matchesArchive(uri, pickedUri)) update(uri, null, pickedUri)
            else _state.update { it.copy(message = EditsMessage.DifferentFile) }
        }
    }

    private fun run(uri: String, decision: suspend EditActions.() -> EditOutcome): Boolean =
        runner.run(EditsRunner.Work(uri, nameOf(uri)), decision)

    private fun finished(result: EditsRunner.Result) {
        runner.resultShown()
        val (work, outcome) = result
        val target = EditsTarget(work.archiveUri, work.archiveName)
        val refused = (outcome as? EditOutcome.Refused)?.result
        _state.update { s ->
            s.copy(
                message = EditsMessage.of(outcome, work.archiveName) ?: s.message,
                password = when (refused) {
                    UpdateResult.NeedsPassword -> target
                    UpdateResult.WrongPassword -> target.copy(wrong = true)
                    else -> s.password
                },
                pick = if (refused == UpdateResult.NotWritable) target else s.pick,
                reload = if (outcome == EditOutcome.Updated) work.archiveUri else s.reload,
            )
        }
    }

    private fun nameOf(uri: String): String = _state.value.archives.firstOrNull { it.archiveUri == uri }?.archiveName.orEmpty()

    private companion object { val WIPED = Char(0) }
}
