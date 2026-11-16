package io.github.martindavinci.lunaris.archives.engine

import android.os.Build
import android.os.Process

/** True inside the isolated :engine process, where start-up work of the main process must not run. */
object EngineProcess {
    private const val PER_USER_RANGE = 100_000      // AOSP UserHandle.PER_USER_RANGE
    private const val FIRST_ISOLATED_UID = 90_000   // AOSP FIRST_APP_ZYGOTE_ISOLATED_UID
    private const val LAST_ISOLATED_UID = 99_999

    fun isCurrent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Process.isIsolated()
        else (Process.myUid() % PER_USER_RANGE) in FIRST_ISOLATED_UID..LAST_ISOLATED_UID
}
