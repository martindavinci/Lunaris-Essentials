package io.github.martindavinci.lunaris.pdf.feature.work

import io.github.martindavinci.lunaris.pdf.pages.OutputFiles
import io.github.martindavinci.lunaris.pdf.pages.PageJob
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.pages.WorkResult
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class JobKind { SAVE, EXTRACT, SPLIT, CREATE }

/** A new file to fill. */
data class JobTarget(val uri: String, val name: String)

/** A job and the uris of its sources, in the order the job counts them. */
data class JobPlan(val job: PageJob, val sources: List<String>)

/** Why a plan could not be made. */
class JobPlanFailure(val failure: WorkFailure, val source: Int?) : Exception()

/** What the sheet over the screen shows. */
sealed interface WorkState {
    val kind: JobKind

    data class Running(
        override val kind: JobKind,
        val name: String,
        val folder: String?,
        val sources: List<String>?,
        val fraction: Float = 0f,
        val page: Int = 0,
        val pages: Int = 0,
        val output: Int = 0,
        val outputs: Int = 1,
        val preparing: Int? = null,
        val preparingOf: Int = 0,
    ) : WorkState

    data class Saved(
        override val kind: JobKind,
        val name: String,
        val folder: String?,
        val pages: Int,
        val bytes: Long?,
        val files: Int,
        val uri: String,
    ) : WorkState

    data class Failed(
        override val kind: JobKind,
        val failure: WorkFailure,
        val source: Int?,
        val neededBytes: Long? = null,
        val freeBytes: Long? = null,
    ) : WorkState
}

/**
 * One job from the moment its targets are chosen to the moment its sheet closes. Targets that were empty
 * are taken away if the job does not finish, so that no empty file is left behind. They wait through a
 * failure, for another try.
 */
class JobSession(
    private val scope: CoroutineScope,
    private val runner: WorkRunner,
    private val files: OutputFiles,
    private val onSaved: suspend (JobKind) -> Unit,
    private val update: (WorkState?) -> Unit,
) {
    private var kind = JobKind.SAVE
    private var targets = emptyList<JobTarget>()
    private var madeForThis = emptyList<String>()
    private var plan: suspend ((Int, Int) -> Unit) -> JobPlan? = { null }
    private var planning: Deferred<JobPlan?>? = null
    private var running: WorkState.Running? = null

    /** [makePlan] may take long, and tells which item it is on, of how many. */
    fun start(kind: JobKind, targets: List<JobTarget>, makePlan: suspend ((Int, Int) -> Unit) -> JobPlan?) {
        this.kind = kind
        this.targets = targets
        plan = makePlan
        madeForThis = emptyList()
        scope.launch {
            madeForThis = targets.filter { files.isEmpty(it.uri) }.map { it.uri }
            run()
        }
    }

    fun retry() {
        scope.launch { run() }
    }

    fun cancel() {
        planning?.cancel()
        runner.cancel()
    }

    fun closeSaved() = update(null)

    fun closeFailure() {
        update(null)
        scope.launch { removeWhatWasMade() }
    }

    private suspend fun run() {
        val first = targets.first()
        val folder = files.describe(first.uri)?.folder
        show(WorkState.Running(kind, first.name, folder, sources = null, outputs = targets.size))
        // Under its own supervisor: a plan that fails is an answer here, and must not end the whole screen's scope.
        val preparing = scope.async(SupervisorJob(scope.coroutineContext[Job])) {
            plan { item, of -> running?.let { show(it.copy(preparing = item, preparingOf = of)) } }
        }
        planning = preparing
        val planned = try {
            preparing.await()
        } catch (_: CancellationException) {
            return endCancelled()
        } catch (failure: JobPlanFailure) {
            update(WorkState.Failed(kind, failure.failure, failure.source))
            return
        } finally {
            planning = null
        }
        planned ?: return endCancelled()
        running?.let { show(it.copy(sources = planned.sources, preparing = null)) }
        when (val result = runner.run(planned.job, ::onProgress, ::saveOutput)) {
            WorkResult.Done -> finish(planned.job.pageCount)
            WorkResult.Cancelled -> endCancelled()
            is WorkResult.Failed -> update(WorkState.Failed(kind, result.failure, result.source, result.neededBytes, result.freeBytes))
        }
    }

    private fun show(state: WorkState.Running) {
        running = state
        update(state)
    }

    private fun onProgress(event: WorkEvent.Progress) {
        running?.let { show(it.copy(fraction = event.fraction, page = event.page, pages = event.pages, output = event.output)) }
    }

    private suspend fun saveOutput(output: Int, file: String): Boolean {
        val target = targets.getOrNull(output) ?: return false
        running?.let { show(it.copy(name = target.name)) }
        return files.save(file, target.uri)
    }

    private suspend fun finish(pages: Int) {
        val target = targets.first()
        val info = files.describe(target.uri)
        val name = info?.name?.takeIf { it.isNotBlank() } ?: target.name
        if (targets.size == 1) files.remember(target.uri, name, pages)
        madeForThis = emptyList()
        onSaved(kind)
        update(WorkState.Saved(kind, name, info?.folder, pages, info?.bytes, targets.size, target.uri))
    }

    private suspend fun endCancelled() {
        update(null)
        removeWhatWasMade()
    }

    private suspend fun removeWhatWasMade() {
        madeForThis.forEach { files.delete(it) }
        madeForThis = emptyList()
    }
}
