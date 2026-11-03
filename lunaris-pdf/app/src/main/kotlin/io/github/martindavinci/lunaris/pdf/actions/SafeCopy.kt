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
     * On failure only a target made for this copy goes. One that had content is the user's: it is kept
     * in [backup] during the write, as Android offers no way to replace a document in one step. A target
     * that cannot be read is written all the same, as it was asked to be.
     */
    fun save(
        temp: File,
        backup: File,
        openSource: () -> InputStream,
        openTarget: () -> OutputStream,
        readTarget: () -> InputStream,
        targetHadContent: Boolean,
        deleteTarget: () -> Unit,
    ): Boolean {
        var backedUp = false
        val saved = attempt {
            checkNotNull(temp.parentFile).mkdirs()
            openSource().use { input -> temp.outputStream().use { input.copyTo(it) } }
            backedUp = targetHadContent && attempt {
                readTarget().use { input -> backup.outputStream().use { input.copyTo(it) } }
            }
            temp.inputStream().use { input -> openTarget().use { input.copyTo(it) } }
        }
        if (!saved && backedUp) attempt { backup.inputStream().use { input -> openTarget().use { input.copyTo(it) } } }
        temp.delete()
        backup.delete()
        if (!saved && !targetHadContent) attempt(deleteTarget)
        return saved
    }
}
