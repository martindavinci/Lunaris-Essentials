package io.github.martindavinci.lunaris.pdf.actions

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Providers fail in many ways. None of them should end the app. */
internal inline fun attempt(action: () -> Unit): Boolean = try {
    action()
    true
} catch (_: IOException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
} catch (_: IllegalStateException) {
    false
} catch (_: UnsupportedOperationException) {
    false
}

internal object SafeCopy {
    /**
     * Reads the whole source into [temp] before opening the target, which empties it. The two can be
     * the same file: the system picker lets the user choose an existing one, even the one being read.
     * On failure only a target made for this copy goes; one that had content is the user's.
     */
    fun save(
        temp: File,
        openSource: () -> InputStream,
        openTarget: () -> OutputStream,
        targetHadContent: Boolean,
        deleteTarget: () -> Unit,
    ): Boolean {
        val saved = attempt {
            checkNotNull(temp.parentFile).mkdirs()
            openSource().use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.inputStream().use { input -> openTarget().use { input.copyTo(it) } }
        }
        temp.delete()
        if (!saved && !targetHadContent) attempt(deleteTarget)
        return saved
    }
}
