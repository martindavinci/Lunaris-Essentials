package io.github.martindavinci.lunaris.archives.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Runs in the isolated :engine process: no permissions, no files except the descriptors it is given.
 *
 * - a job is registered on the binder thread, before it is queued, so a cancel that arrives while
 *   the job waits for a pool thread is not lost;
 * - a job that ignores a cancel for [Limits.ENGINE_CANCEL_GRACE_MILLIS] ends the process. Key
 *   derivation in Commons Compress and decoder loops cannot be interrupted, and the process
 *   exists to be thrown away. A second job running at that moment dies with it and its caller
 *   sees "reader stopped";
 * - every way a reader can fail ends with a STATUS message, so a damaged archive never looks
 *   like a dead engine; unexpected runtime exceptions on hostile input are "damaged", not a crash.
 */
class EngineService : Service() {
    private fun interface Work { fun run(job: EngineJob, volume: EngineVolume, out: WireWriter): EngineStatus }

    private val pool = Executors.newFixedThreadPool(THREADS)
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val jobs = ConcurrentHashMap<Long, EngineJob>()

    private val binder = object : IArchiveEngine.Stub() {
        override fun protocolVersion() = EngineWire.VERSION

        override fun list(jobId: Long, volumes: Array<ParcelFileDescriptor>?, route: Int, password: CharArray?, sink: ParcelFileDescriptor?) =
            submit(jobId, volumes, password, sink) { job, volume, out ->
                IsolatedReaders.of(EngineRoute.of(route)).list(job, volume, password, out)
            }

        override fun extract(
            jobId: Long, volumes: Array<ParcelFileDescriptor>?, route: Int, password: CharArray?,
            indices: LongArray?, sink: ParcelFileDescriptor?,
        ) = submit(jobId, volumes, password, sink) { job, volume, out ->
            IsolatedReaders.of(EngineRoute.of(route)).extract(job, volume, password, indices ?: LongArray(0), null, out)
        }

        override fun cancel(jobId: Long) {
            val job = jobs[jobId] ?: return
            job.cancel()
            val giveUp = Runnable { if (jobs[jobId] === job) Process.killProcess(Process.myPid()) }
            watchdog.schedule(giveUp, Limits.ENGINE_CANCEL_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        pool.shutdown()
        watchdog.shutdown()
        super.onDestroy()
    }

    private fun submit(jobId: Long, volumes: Array<ParcelFileDescriptor>?, password: CharArray?, sink: ParcelFileDescriptor?, work: Work) {
        val job = EngineJob(jobId)
        jobs[jobId] = job
        try {
            pool.execute { run(job, volumes, password, sink, work) }
        } catch (_: RejectedExecutionException) {
            release(job, volumes, password, sink)
        }
    }

    private fun run(job: EngineJob, volumes: Array<ParcelFileDescriptor>?, password: CharArray?, sink: ParcelFileDescriptor?, work: Work) {
        try {
            val first = volumes?.firstOrNull() ?: return
            val pipe = sink ?: return
            val channel = FileInputStream(first.fileDescriptor).channel
            WireWriter(ParcelFileDescriptor.AutoCloseOutputStream(pipe)).use { out ->
                out.status(statusOf(job) { work.run(job, EngineVolume(first.fd, channel), out) })
            }
        } catch (_: SinkClosedException) {
            // The main process closed its end: nobody is listening.
        } catch (_: IOException) {
            // The same, noticed while writing the status or closing the pipe.
        } finally {
            release(job, volumes, password, sink)
        }
    }

    /** Everything a reader can throw becomes a status, except a closed pipe. */
    private inline fun statusOf(job: EngineJob, work: () -> EngineStatus): EngineStatus = try {
        work()
    } catch (e: SinkClosedException) {
        throw e
    } catch (_: CancellationException) {
        EngineStatus.CANCELLED
    } catch (_: UnsupportedOperationException) {
        EngineStatus.UNSUPPORTED_FEATURE
    } catch (_: OutOfMemoryError) {
        EngineStatus.OUT_OF_MEMORY
    } catch (_: IOException) {
        if (job.cancelled) EngineStatus.CANCELLED else EngineStatus.DAMAGED
    } catch (_: RuntimeException) {
        if (job.cancelled) EngineStatus.CANCELLED else EngineStatus.DAMAGED
    }

    private fun release(job: EngineJob, volumes: Array<ParcelFileDescriptor>?, password: CharArray?, sink: ParcelFileDescriptor?) {
        password?.fill(WIPED)
        volumes?.forEach { runCatching { it.close() } }
        runCatching { sink?.close() }
        jobs.remove(job.id, job)
    }

    private companion object {
        const val THREADS = 2
        val WIPED = Char(0)
    }
}
