package io.github.martindavinci.lunaris.archives.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.archives.safety.Limits
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The binding to the isolated engine. The engine stays bound while a [use] block runs, and the
 * idle clock starts only when the last block has ended: a listing or an extraction longer than
 * the idle time must not lose its service in the middle.
 */
@Singleton
class EngineConnection @Inject constructor(@param:ApplicationContext private val context: Context) {
    private class Bound(val engine: IArchiveEngine, val connection: ServiceConnection)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binding = Mutex()
    private val lock = Any()
    private var bound: Bound? = null
    private var users = 0
    private var idle: Job? = null

    /** Runs [block] with the engine bound for as long as it takes: a whole job, not one call. */
    suspend fun <T> use(block: suspend (IArchiveEngine) -> T): T {
        synchronized(lock) {
            users++
            idle?.cancel()
            idle = null
        }
        try {
            return block(connected())
        } catch (_: DeadObjectException) {
            drop()
            throw EngineStoppedException()
        } finally {
            synchronized(lock) {
                users--
                if (users == 0) idle = scope.launch { unbindWhenIdle() }
            }
        }
    }

    /** Lets go of the engine now: after it died, or when it does not answer. The next [use] binds again. */
    fun drop() {
        val old = synchronized(lock) { bound.also { bound = null } }
        old?.let { unbind(it.connection) }
    }

    private suspend fun unbindWhenIdle() {
        delay(Limits.ENGINE_IDLE_UNBIND_MILLIS)
        val old = synchronized(lock) { if (users == 0) bound.also { bound = null } else null }
        old?.let { unbind(it.connection) }
    }

    private suspend fun connected(): IArchiveEngine = binding.withLock {
        synchronized(lock) { bound }?.takeIf { it.engine.asBinder().isBinderAlive }?.let { return@withLock it.engine }
        drop()
        val fresh = withTimeoutOrNull(Limits.ENGINE_BIND_TIMEOUT_MILLIS) { bind() } ?: throw EngineStoppedException()
        val version = withContext(Dispatchers.IO) { runCatching { fresh.engine.protocolVersion() }.getOrNull() }
        if (version != EngineWire.VERSION) {
            unbind(fresh.connection)
            throw EngineStoppedException()
        }
        synchronized(lock) { bound = fresh }
        fresh.engine
    }

    /** Suspends until the service is connected; the caller's timeout is what ends a bind that never answers. */
    private suspend fun bind(): Bound = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (continuation.isActive) continuation.resume(Bound(IArchiveEngine.Stub.asInterface(service), this))
            }
            // The system would start the service again by itself: after a crash on a hostile
            // archive that is a crash loop. Unbind instead; the next call decides.
            override fun onServiceDisconnected(name: ComponentName) = forget(this)
            override fun onBindingDied(name: ComponentName) = forget(this)
            override fun onNullBinding(name: ComponentName) = forget(this)
        }
        continuation.invokeOnCancellation { unbind(connection) }
        val intent = Intent(context, EngineService::class.java)
        val started = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!started) {
            unbind(connection)
            if (continuation.isActive) continuation.resumeWithException(EngineStoppedException())
        }
    }

    private fun forget(connection: ServiceConnection) {
        synchronized(lock) { if (bound?.connection === connection) bound = null }
        unbind(connection)
    }

    /** Safe to call twice and for a connection that never bound. */
    private fun unbind(connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
    }
}
