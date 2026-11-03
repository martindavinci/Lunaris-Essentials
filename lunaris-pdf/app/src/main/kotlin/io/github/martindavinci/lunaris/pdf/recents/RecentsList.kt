package io.github.martindavinci.lunaris.pdf.recents

/** The rules of the recents list, newest first. */
object RecentsList {
    const val MAX_ENTRIES = 50
    const val MAX_PINNED = 3

    /** Puts the document on top, keeping where it was left if it was there already. A pin is never dropped. */
    fun touch(entries: List<RecentEntry>, visit: RecentVisit, nowMillis: Long): List<RecentEntry> {
        val known = entries.firstOrNull { it.uri == visit.uri } ?: RecentEntry(visit.uri)
        val updated = known.copy(
            name = visit.name,
            lastOpenedMillis = nowMillis,
            pages = visit.pages,
            locked = visit.locked,
            chapter = if (visit.locked) "" else known.chapter,
        )
        val list = listOf(updated) + entries.filterNot { it.uri == visit.uri }
        if (list.size <= MAX_ENTRIES) return list
        val spare = list.indexOfLast { !it.pinned }
        return list.filterIndexed { index, _ -> index != spare }
    }

    fun savePosition(entries: List<RecentEntry>, uri: String, page: Int, scale: String, chapter: String): List<RecentEntry> =
        entries.map { if (it.uri == uri) it.copy(page = page, scale = scale, chapter = chapter) else it }

    fun remove(entries: List<RecentEntry>, uri: String): List<RecentEntry> = entries.filterNot { it.uri == uri }

    /** Null when there are as many pins as allowed already. */
    fun setPinned(entries: List<RecentEntry>, uri: String, on: Boolean): List<RecentEntry>? {
        if (on && entries.count { it.pinned && it.uri != uri } >= MAX_PINNED) return null
        return entries.map { if (it.uri == uri) it.copy(pinned = on) else it }
    }

    fun restore(entries: List<RecentEntry>, entry: RecentEntry): List<RecentEntry> =
        if (entries.any { it.uri == entry.uri }) entries else (entries + entry).sortedByDescending { it.lastOpenedMillis }
}
