package io.github.martindavinci.lunaris.archives.browse

import java.util.Locale

/** A row whose name contains the query, and where. */
data class SearchHit(val row: FolderRow, val match: IntRange)

/** Names across the whole archive, folders before their contents. */
object EntrySearch {
    const val MIN_QUERY = 1
    private const val DEFAULT_LIMIT = 500

    fun search(view: FolderView, query: String, limit: Int = DEFAULT_LIMIT): List<SearchHit> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.length < MIN_QUERY) return emptyList()
        return view.walk()
            .mapNotNull { row ->
                val start = row.name.lowercase(Locale.ROOT).indexOf(needle)
                if (start < 0) null else SearchHit(row, start until start + needle.length)
            }
            .take(limit)
            .toList()
    }
}
