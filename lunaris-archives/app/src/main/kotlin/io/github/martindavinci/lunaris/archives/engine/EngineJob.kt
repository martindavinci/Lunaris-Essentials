package io.github.martindavinci.lunaris.archives.engine

import kotlinx.coroutines.CancellationException

/**
 * One list or extract call inside the engine process.
 *
 * The cancel hook is set and cleared under a lock: once [detach] returns, no binder thread can
 * still be inside it, so the native job it points at may be freed.
 */
class EngineJob(val id: Long) {
    @Volatile var cancelled: Boolean = false
        private set
    private var onCancel: (() -> Unit)? = null

    /** Registers what stops the reader. False when the job was cancelled before it could start. */
    @Synchronized
    fun attach(hook: () -> Unit): Boolean {
        if (cancelled) return false
        onCancel = hook
        return true
    }

    /** After this returns the hook is never called again: its native job may be freed. */
    @Synchronized
    fun detach() { onCancel = null }

    @Synchronized
    fun cancel() {
        cancelled = true
        onCancel?.invoke()
    }

    fun throwIfCancelled() {
        if (cancelled) throw CancellationException("cancelled")
    }
}
