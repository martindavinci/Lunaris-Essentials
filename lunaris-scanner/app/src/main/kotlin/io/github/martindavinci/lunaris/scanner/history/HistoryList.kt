package io.github.martindavinci.lunaris.scanner.history

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/** The rules of the history list, newest first. */
object HistoryList {
    const val MAX_ENTRIES = 500

    /** A code equal to the newest entry only refreshes its timestamp; anything else goes on top. */
    fun add(entries: List<HistoryEntry>, code: ScannedCode, nowMillis: Long): List<HistoryEntry> {
        val newest = entries.firstOrNull()
        if (newest != null && newest.text == code.text && newest.format == code.format) {
            return listOf(newest.copy(timestampMillis = nowMillis)) + entries.drop(1)
        }
        val id = (entries.maxOfOrNull { it.id } ?: 0L) + 1
        return (listOf(HistoryEntry(id, code.text, code.format, nowMillis)) + entries).take(MAX_ENTRIES)
    }

    fun delete(entries: List<HistoryEntry>, id: Long): List<HistoryEntry> = entries.filterNot { it.id == id }
}
