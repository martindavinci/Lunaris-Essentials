package io.github.martindavinci.lunaris.pdf.actions

import java.io.File

/** Where a copy being shared lives: its own folder in the cache, under a name that came from outside. */
object SharedPdfFiles {
    const val KEEP_MILLIS = 24L * 60 * 60 * 1000
    private const val SHARE_FOLDER = "share"
    private const val EXTENSION = ".pdf"
    private const val FALLBACK_NAME = "document"

    fun target(cacheDir: File, displayName: String, folderName: String): File =
        File(File(File(cacheDir, SHARE_FOLDER), folderName), safeName(displayName))

    /** The receiving app may read the file for a while, so it stays a day and goes with the next share. */
    fun removeOld(cacheDir: File, nowMillis: Long) {
        File(cacheDir, SHARE_FOLDER).listFiles()
            ?.filter { nowMillis - it.lastModified() > KEEP_MILLIS }
            ?.forEach { it.deleteRecursively() }
    }

    private fun safeName(displayName: String): String {
        val lastPart = displayName.substringAfterLast('/').substringAfterLast('\\')
        val clean = lastPart.filterNot { it.isISOControl() }.trim().trim('.')
        val name = clean.ifEmpty { FALLBACK_NAME }
        return if (name.endsWith(EXTENSION, ignoreCase = true)) name else name + EXTENSION
    }
}
