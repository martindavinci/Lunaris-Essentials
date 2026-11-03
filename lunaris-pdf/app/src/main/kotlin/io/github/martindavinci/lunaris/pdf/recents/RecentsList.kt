package io.github.martindavinci.lunaris.pdf.recents

/** The rules of the recents list, newest first. */
object RecentsList {
    const val MAX_ENTRIES = 50

    /** Puts the document on top, keeping where it was left if it was there already. */
    fun touch(entries: List<RecentEntry>, uri: String, name: String, nowMillis: Long): List<RecentEntry> {
        val known = entries.firstOrNull { it.uri == uri } ?: RecentEntry(uri)
        val updated = known.copy(name = name, lastOpenedMillis = nowMillis)
        return (listOf(updated) + entries.filterNot { it.uri == uri }).take(MAX_ENTRIES)
    }

    fun savePosition(entries: List<RecentEntry>, uri: String, page: Int, scale: String): List<RecentEntry> =
        entries.map { if (it.uri == uri) it.copy(page = page, scale = scale) else it }

    fun remove(entries: List<RecentEntry>, uri: String): List<RecentEntry> = entries.filterNot { it.uri == uri }
}
