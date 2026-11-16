package io.github.martindavinci.lunaris.archives.extract

import android.system.ErrnoException
import android.system.OsConstants

/** Tells a full disk from other write errors. */
object FreeSpace {
    private const val NO_SPACE_MARK = "ENOSPC"

    fun isNoSpace(e: Throwable): Boolean = generateSequence(e) { it.cause }.any { cause ->
        cause.message?.contains(NO_SPACE_MARK) == true || (cause is ErrnoException && cause.errno == OsConstants.ENOSPC)
    }
}
