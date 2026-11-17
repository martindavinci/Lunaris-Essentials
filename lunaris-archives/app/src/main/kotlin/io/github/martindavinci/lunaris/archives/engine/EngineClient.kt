package io.github.martindavinci.lunaris.archives.engine

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main-process client of the engine:
 * - the engine stays bound for the whole job (see EngineConnection), not for the oneway call;
 * - a cancelled coroutine tells the engine to stop even while this side is blocked reading the
 *   pipe, so a listing can be cancelled, and so can "Opening…" while a solid archive skips
 *   towards the entry;
 * - the archive descriptor and both pipe ends are closed on every path;
 * - an ended stream after our own cancel is a cancellation, not a death of the engine.
 */
@Singleton
class EngineClient @Inject constructor(private val connection: EngineConnection) : ArchiveEngine {
    private val ids = AtomicLong()
    private val deaths = ConcurrentHashMap<String, Int>()

    override suspend fun list(source: ArchiveSource, route: EngineRoute, password: CharArray?, onEntries: (Int) -> Unit): ListResult {
        if ((deaths[source.key] ?: 0) >= Limits.ENGINE_MAX_DEATHS) return ListResult.Problem(ArchiveProblem.READER_STOPPED)
        val collected = try {
            connection.use { engine ->
                val jobId = ids.incrementAndGet()
                val pipe = start(source) { archive, sink -> engine.list(jobId, arrayOf(archive), route.code, password, sink) }
                pipe.use { read -> readOrStop(engine, jobId) { WireCollector.index(WireReader(FileInputStream(read.fileDescriptor)), source.kindHint, onEntries) } }
            }
        } catch (_: FileNotFoundException) {
            return ListResult.Problem(ArchiveProblem.GONE)
        } catch (_: SecurityException) {
            return ListResult.Problem(ArchiveProblem.GONE)
        } catch (e: EngineStoppedException) {
            deaths.merge(source.key, 1, Int::plus)
            throw e
        }
        if (collected.status == EngineStatus.CANCELLED) {
            currentCoroutineContext().ensureActive()   // our own cancel: let it through
            throw EngineStoppedException()             // the engine was taken away under this job
        }
        return ListMapping.toResult(collected.status, collected.index?.kind ?: source.kindHint, route, collected.index)
    }

    override fun extract(
        source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>,
    ): Flow<EngineEvent> = flow {
        val batches = IndexBatches.of(indices)
        for ((n, batch) in batches.withIndex()) {
            var status = EngineStatus.OK
            pass(source, route, password, batch, expected) { event ->
                if (event is EngineEvent.Finished) status = event.status
                // One Finished for the whole extraction: the last batch's, or the first that is not OK.
                if (event !is EngineEvent.Finished || n == batches.lastIndex || status != EngineStatus.OK) emit(event)
            }
            if (status != EngineStatus.OK) break
        }
    }.flowOn(Dispatchers.IO)

    /** One engine pass over [indices], small enough for one binder call. */
    private suspend fun pass(
        source: ArchiveSource, route: EngineRoute, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>, onEvent: suspend (EngineEvent) -> Unit,
    ) {
        connection.use { engine ->
            val jobId = ids.incrementAndGet()
            val pipe = start(source) { archive, sink -> engine.extract(jobId, arrayOf(archive), route.code, password, indices, sink) }
            pipe.use { read ->
                coroutineScope {
                    val watcher = launch { stopEngineWhenCancelled(engine, jobId) }
                    try {
                        WireCollector.events(WireReader(FileInputStream(read.fileDescriptor)), expected).forEach { onEvent(it) }
                    } catch (_: WireFormatException) {
                        currentCoroutineContext().ensureActive()   // our own cancel ended the stream
                        deaths.merge(source.key, 1, Int::plus)
                        throw EngineStoppedException()
                    } finally {
                        watcher.cancel()
                    }
                }
            }
        }
    }

    /**
     * Opens the archive and a reliable pipe, gives both to the engine and returns the read side.
     * The engine has its own copies when the call returns: ours are closed here.
     */
    private suspend fun start(source: ArchiveSource, call: (ParcelFileDescriptor, ParcelFileDescriptor) -> Unit): ParcelFileDescriptor =
        withContext(Dispatchers.IO) {
            val (read, write) = ParcelFileDescriptor.createReliablePipe()
            try {
                source.openFd().use { archive -> call(archive, write) }
                read
            } catch (_: RemoteException) {
                read.close()
                connection.drop()
                throw EngineStoppedException()
            } catch (e: Throwable) {
                read.close()
                throw e
            } finally {
                write.close()
            }
        }

    /** Runs the blocking [read] on IO. If the caller is cancelled meanwhile, the engine is told, which ends the read. */
    private suspend fun <T> readOrStop(engine: IArchiveEngine, jobId: Long, read: () -> T): T = coroutineScope {
        val reading = async(Dispatchers.IO) {
            try { read() } catch (_: WireFormatException) { throw EngineStoppedException() }
        }
        try {
            reading.await()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runCatching { engine.cancel(jobId) } }
            throw e
        }
    }

    private suspend fun stopEngineWhenCancelled(engine: IArchiveEngine, jobId: Long) {
        try {
            awaitCancellation()
        } finally {
            // Also runs when the job simply ended: cancelling a job the engine no longer has is a no-op.
            withContext(NonCancellable) { runCatching { engine.cancel(jobId) } }
        }
    }
}
