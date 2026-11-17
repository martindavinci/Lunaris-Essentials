package io.github.martindavinci.lunaris.archives.extract

import android.system.ErrnoException
import android.system.OsConstants

/** Tells a full disk from other write errors. */
object FreeSpace {
    // "ENOSPC" from Os and the streams; the plain text when a provider passes its IOException on as a message.
    private val NO_SPACE_MARKS = listOf("ENOSPC", "No space left")

    fun isNoSpace(e: Throwable): Boolean = generateSequence(e) { it.cause }.any { cause ->
        NO_SPACE_MARKS.any { cause.message?.contains(it) == true } || (cause is ErrnoException && cause.errno == OsConstants.ENOSPC)
    }
}
