package io.github.martindavinci.lunaris.pdf.actions

/** What the Reader does with the file itself. */
interface DocumentActions {
    /** False if the copy could not be written; nothing is left behind then. */
    suspend fun saveCopy(from: String, to: String): Boolean

    /** A uri another app may read, or null if the copy could not be made. */
    suspend fun prepareShare(from: String, name: String): String?
}
