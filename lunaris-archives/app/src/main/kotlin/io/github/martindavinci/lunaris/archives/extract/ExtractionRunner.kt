package io.github.martindavinci.lunaris.archives.extract

import io.github.martindavinci.lunaris.archives.engine.ArchiveEngine
import io.github.martindavinci.lunaris.archives.engine.EngineStatus
import io.github.martindavinci.lunaris.archives.engine.EngineStoppedException
import io.github.martindavinci.lunaris.archives.engine.EntryRoutes
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.RatioGuard
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One extraction at a time: plan, conflicts decided before any write, one engine pass per route. */
@Singleton
class ExtractionRunner internal constructor(
    private val engine: ArchiveEngine,
    private val marker: ExtractMarker,
    dispatcher: CoroutineDispatcher,
    private val newGuard: () -> RatioGuard,
) {
    @Inject constructor(engine: ArchiveEngine, marker: ExtractMarker) : this(engine, marker, Dispatchers.IO, { RatioGuard() })

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    private var job: ExtractJob? = null

    /** The extraction being shown: its archive and destination. */
    val currentJob: ExtractJob? get() = job
    private var plan: ExtractionPlan? = null
    private var existing: Map<List<String>, ExistingNames> = emptyMap()
    @Volatile private var running: Job? = null
    @Volatile private var planning = false
    @Volatile private var large: CompletableDeferred<Boolean>? = null

    /** The plan and one query per destination folder run in the background: a big tree takes seconds. */
    fun start(job: ExtractJob) {
        if (_state.value !is ExtractionState.Idle) return
        this.job = job
        _state.value = ExtractionState.Running(0, job.entries.size, 0, 0, "")
        // One job from planning to the last file: a Stop while the folders are read stops it all.
        planning = true
        running = scope.launch {
            val conflicts = try {
                val planned = ExtractionPlan.of(job.entries).also { plan = it }
                val folders = (listOf(emptyList<String>()) + planned.folders + planned.files.map { it.folder }).distinct()
                existing = folders.mapNotNull { folder -> ensureActive(); job.target.children(folder)?.let { folder to it } }.toMap()
                ConflictPlan.conflicts(planned, existing).also { planning = false }
            } catch (_: CancellationException) {
                stoppedWhilePlanning()
                return@launch
            } catch (e: Exception) {   // FolderGoneException, or a provider's own exception while the folders are read
                planning = false
                job.password?.fill(WIPED)
                _state.value = ExtractionState.Failed(if (FreeSpace.isNoSpace(e)) ExtractFailure.RAN_OUT_OF_SPACE else ExtractFailure.FOLDER_GONE)
                return@launch
            }
            if (conflicts.isEmpty()) execute(emptyMap()) else _state.value = ExtractionState.Conflicts(conflicts, current = 0)
        }
    }

    fun resolve(choices: Map<PlannedFile, ConflictChoice>) {
        if (_state.value !is ExtractionState.Conflicts) return
        val plan = plan ?: return
        _state.value = ExtractionState.Running(0, plan.files.size, 0, plan.declaredBytes, "")   // a second tap finds no dialog
        running = scope.launch { execute(choices) }
    }

    fun cancelConflicts() {
        job?.password?.fill(WIPED)
        _state.value = ExtractionState.Idle
    }

    fun answerLarge(continueIt: Boolean) {
        large?.complete(continueIt)
    }

    fun stop() {
        large?.complete(false)
        running?.cancel()
        if (planning) stoppedWhilePlanning()   // also when the job was cancelled before it started
    }

    /** Stop while the folders were read: nothing was written. */
    private fun stoppedWhilePlanning() {
        val job = job ?: return
        planning = false
        job.password?.fill(WIPED)
        _state.value = ExtractionState.Finished(0, job.entries.size, emptyList(), emptyList(), job.target.label, stoppedEarly = true)
    }

    fun reset() {
        if (_state.value is ExtractionState.Finished || _state.value is ExtractionState.Failed) _state.value = ExtractionState.Idle
    }

    /** Resolves the conflicts (a pass over every file), then writes. Runs inside [running]. */
    private suspend fun execute(choices: Map<PlannedFile, ConflictChoice>) {
        val job = job ?: return
        val plan = plan ?: return
        val resolved = ConflictPlan.resolve(plan, existing, choices)
        _state.value = ExtractionState.Running(0, resolved.size, 0, plan.declaredBytes, "")
        var writer: ExtractionWriter? = null
        try {
            if (!enoughSpace(job.target, resolved, plan.declaredBytes)) return
            val built = writer(job, resolved, plan).also { writer = it }
            val failure = passes(job, resolved, built)
            if (failure == ExtractFailure.WRONG_PASSWORD) job.onWrongPassword()
            _state.value = failure?.let { ExtractionState.Failed(it) } ?: finished(job, plan, resolved, built, stopped = false)
        } catch (_: CancellationException) {
            writer?.abandon()
            _state.value = writer?.let { finished(job, plan, resolved, it, stopped = true) } ?: ExtractionState.Idle
        } catch (e: FolderGoneException) {
            writer?.abandon()
            _state.value = ExtractionState.Failed(ExtractFailure.FOLDER_GONE)
        } catch (e: EngineStoppedException) {
            writer?.abandon()
            _state.value = ExtractionState.Failed(ExtractFailure.READER_STOPPED)
        } catch (e: SecurityException) {
            // The destination maps its own failures to FolderGoneException: this is the archive's permission.
            writer?.abandon()
            _state.value = ExtractionState.Failed(ExtractFailure.READER_STOPPED)
        } catch (e: FileNotFoundException) {
            writer?.abandon()
            _state.value = ExtractionState.Failed(ExtractFailure.READER_STOPPED)
        } catch (e: IOException) {
            writer?.abandon()
            _state.value = ExtractionState.Failed(if (FreeSpace.isNoSpace(e)) ExtractFailure.RAN_OUT_OF_SPACE else ExtractFailure.FOLDER_GONE)
        } catch (e: RuntimeException) {
            // DocumentsContract passes a provider's own exceptions on (IllegalStateException "Failed to touch …",
            // UnsupportedOperationException, NullPointerException): a failed run, never a crash.
            writer?.abandon()
            _state.value = ExtractionState.Failed(if (FreeSpace.isNoSpace(e)) ExtractFailure.RAN_OUT_OF_SPACE else ExtractFailure.FOLDER_GONE)
        } finally {
            marker.clear()
            job.password?.fill(WIPED)
        }
    }

    /**
     * Spec step 6: measured on a file created for that (where the provider can tell), then removed.
     * It has a name of its own, so it never takes the place of a file the run will write or replace.
     */
    private fun enoughSpace(target: SafTarget, resolved: List<ResolvedFile>, declared: Long): Boolean {
        if (resolved.isEmpty()) return true
        val probe = target.createFile(target.ensureFolder(emptyList()), PROBE_NAME)
        val free = target.freeBytes()
        target.delete(probe)
        if (free == null || free >= declared) return true
        _state.value = ExtractionState.Failed(ExtractFailure.NO_SPACE, neededBytes = declared, freeBytes = free)
        return false
    }

    private fun writer(job: ExtractJob, resolved: List<ResolvedFile>, plan: ExtractionPlan): ExtractionWriter {
        val total = (plan.declaredBytes * Limits.TOTAL_EXTRACT_FACTOR).toLong() + Limits.TOTAL_EXTRACT_SLACK_BYTES
        return ExtractionWriter(
            job.target, resolved.associateBy { it.file.entry.index }, newGuard(), total, marker,
            askLarge = { name -> askLarge(name) },
            onProgress = { written, bytes, current ->
                _state.value = ExtractionState.Running(written, resolved.size, bytes, plan.declaredBytes, current)
            },
        )
    }

    /** One engine pass per route; null when every pass ended normally, else why the run failed. */
    private suspend fun passes(job: ExtractJob, resolved: List<ResolvedFile>, writer: ExtractionWriter): ExtractFailure? {
        val byRoute = resolved.groupBy { EntryRoutes.routeFor(job.archive, it.file.entry) ?: job.archive.route }
        for ((route, files) in byRoute.entries.sortedBy { it.key.code }) {
            val indices = files.map { it.file.entry.index }.sorted().toLongArray()
            val expected = files.associate { it.file.entry.index to it.file.entry.rawPath }
            var status: EngineStatus? = null
            try {
                engine.extract(job.archive.source, route, job.password, indices, expected).collect { event ->
                    status = writer.onEvent(event) ?: status
                    if (writer.overTotal) throw OverTotal()
                }
            } catch (_: OverTotal) {
                writer.abandon()
                return null
            }
            when (status) {
                EngineStatus.PATH_MISMATCH, EngineStatus.ACCESS_DENIED -> return ExtractFailure.READER_STOPPED
                EngineStatus.CANCELLED -> throw CancellationException("engine cancelled")
                else -> Unit
            }
        }
        return if (writer.needPassword > 0 && writer.written == 0) ExtractFailure.WRONG_PASSWORD else null
    }

    private fun finished(job: ExtractJob, plan: ExtractionPlan, resolved: List<ResolvedFile>, writer: ExtractionWriter, stopped: Boolean) =
        ExtractionState.Finished(writer.written, resolved.size, plan.skipped, writer.damaged.toList(), job.target.label, stopped)

    private suspend fun askLarge(name: String): Boolean {
        val answer = CompletableDeferred<Boolean>().also { large = it }
        val before = _state.value
        _state.value = ExtractionState.AskLarge(name)
        return answer.await().also {
            large = null
            _state.value = before
        }
    }

    private class OverTotal : RuntimeException("more bytes than the archive declared")

    private companion object {
        val WIPED = Char(0)
        const val PROBE_NAME = ".lunaris-space-check"
    }
}
