package io.github.martindavinci.lunaris.archives.recents

import io.github.martindavinci.lunaris.archives.safety.Limits

/** List rules of the recents: newest first, one row per archive, at most [Limits.RECENTS_MAX]. */
object RecentsHome {
    /** Returns (kept, evicted); the caller releases the permissions of the evicted ones. */
    fun upsert(list: List<RecentArchive>, item: RecentArchive, max: Int = Limits.RECENTS_MAX): Pair<List<RecentArchive>, List<RecentArchive>> {
        val all = listOf(item) + list.filter { it.uri != item.uri }
        return all.take(max) to all.drop(max)
    }
}
