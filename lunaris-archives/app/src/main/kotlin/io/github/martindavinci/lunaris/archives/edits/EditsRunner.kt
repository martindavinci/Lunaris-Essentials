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
    /** [owner] is the screen that asked: its result is for it alone, unless it is gone by then (null: anyone). */
    data class Work(val archiveUri: String, val archiveName: String, val owner: Any? = null)
    data class Result(val work: Work, val outcome: EditOutcome)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val gone = HashSet<Any>()
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
            } catch (_: Exception) {   // a full disk or a provider: a failed decision, never a crash
                EditOutcome.Failed
            }
            synchronized(this@EditsRunner) {
                val owner = work.owner?.takeUnless { gone.remove(it) }
                _result.value = Result(work.copy(owner = owner), outcome)
                _working.value = null
            }
        }
        return true
    }

    /**
     * The start-up work (putting back an archive left half written, then the clean-ups) in this scope: a rotation
     * of the first screen does not cut it short. Nothing it throws ends the app.
     */
    fun atStart(work: suspend () -> Unit) {
        scope.launch {
            try {
                work()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                Unit
            }
        }
    }

    fun resultShown() {
        _result.value = null
    }

    /** The screen [owner] went away: a result still to come is for whichever screen is there. */
    @Synchronized
    fun ownerGone(owner: Any) {
        if (_working.value?.owner === owner) gone += owner
        _result.value?.let { r -> if (r.work.owner === owner) _result.value = r.copy(work = r.work.copy(owner = null)) }
    }
}
