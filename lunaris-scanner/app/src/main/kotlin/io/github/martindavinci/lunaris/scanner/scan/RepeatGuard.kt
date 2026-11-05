package io.github.martindavinci.lunaris.scanner.scan

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/**
 * Keeps a code that is still in front of the camera from coming back the moment the camera reads again.
 * Each code passes again only once it has been out of sight for [quietMillis], so two codes side by
 * side in the viewfinder do not take turns.
 */
class RepeatGuard(private val quietMillis: Long = DEFAULT_QUIET_MILLIS) {
    private val lastSeen = mutableMapOf<ScannedCode, Long>()

    @Synchronized
    fun accept(code: ScannedCode, nowMillis: Long): Boolean {
        lastSeen.values.removeAll { nowMillis - it >= quietMillis }
        return lastSeen.put(code, nowMillis) == null
    }

    /** Starts the quiet time over: the codes last seen before leaving may still be in view. */
    @Synchronized
    fun rearm(nowMillis: Long) {
        lastSeen.replaceAll { _, _ -> nowMillis }
    }

    /** Nobody got to see it, so it counts as not read at all. */
    @Synchronized
    fun forget(code: ScannedCode) {
        lastSeen.remove(code)
    }

    private companion object {
        const val DEFAULT_QUIET_MILLIS = 3_000L
    }
}
