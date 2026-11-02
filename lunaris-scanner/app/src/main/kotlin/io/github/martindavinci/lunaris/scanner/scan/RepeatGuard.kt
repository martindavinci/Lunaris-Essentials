package io.github.martindavinci.lunaris.scanner.scan

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/**
 * Keeps a code that is still in front of the camera from reopening its result the moment the user
 * comes back. The same code passes again only once it has been out of sight for [quietMillis].
 */
class RepeatGuard(private val quietMillis: Long = DEFAULT_QUIET_MILLIS) {
    private var lastCode: ScannedCode? = null
    private var lastSeenMillis = 0L

    @Synchronized
    fun accept(code: ScannedCode, nowMillis: Long): Boolean {
        val repeat = code == lastCode && nowMillis - lastSeenMillis < quietMillis
        lastCode = code
        lastSeenMillis = nowMillis
        return !repeat
    }

    /** Starts the quiet time over: the code last seen before leaving may still be in view. */
    @Synchronized
    fun rearm(nowMillis: Long) {
        lastSeenMillis = nowMillis
    }

    private companion object {
        const val DEFAULT_QUIET_MILLIS = 3_000L
    }
}
