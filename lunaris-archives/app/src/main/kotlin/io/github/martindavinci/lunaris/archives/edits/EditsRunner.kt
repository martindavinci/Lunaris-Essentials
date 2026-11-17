package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.di.Background
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs one decision about changed files at a time, in a scope of its own: leaving the screen that started an
 * update never stops it halfway. Home and the browser both watch [working] and [result].
 */
@Singleton
class EditsRunner @Inject constructor(private val actions: EditActions, @param:Background dispatcher: CoroutineDispatcher) {
    data class Work(val archiveUri: String, val archiveName: String)
    data class Result(val work: Work, val outcome: EditOutcome)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _working = MutableStateFlow<Work?>(null)
    val working: StateFlow<Work?> = _working.asStateFlow()
    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    /** False when another decision is still running. */
    fun run(work: Work, decision: suspend EditActions.() -> EditOutcome): Boolean {
        synchronized(this) {
            if (_working.value != null) return false
            _working.value = work
        }
        scope.launch {
            val outcome = try {
                actions.decision()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                EditOutcome.Failed
            }
            _result.value = Result(work, outcome)
            _working.value = null
        }
        return true
    }

    fun resultShown() {
        _result.value = null
    }
}
