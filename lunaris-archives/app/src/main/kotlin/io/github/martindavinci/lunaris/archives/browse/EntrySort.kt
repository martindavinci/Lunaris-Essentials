package io.github.martindavinci.lunaris.archives.browse

import java.util.Locale

/**
 * Folders first (unless asked otherwise), then the chosen field, then the name. Descending reverses
 * only the field; unknown dates stay last either way.
 */
object EntrySort {
    private class Keyed(val row: FolderRow) {
        val nameKey = NaturalOrder.key(row.name)
        val type = if (row.isFolder) "" else row.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val date = row.entry?.modifiedMillis
    }

    fun sort(rows: List<FolderRow>, order: SortOrder): List<FolderRow> {
        val byName = Comparator<Keyed> { a, b -> NaturalOrder.compare(a.row.name, a.nameKey, b.row.name, b.nameKey) }
        val field = fieldComparator(order.field, byName).let { if (order.ascending) it else it.reversed() }
        val withDates = if (order.field == SortField.DATE) nullDatesLast().then(field) else field
        val full = if (order.foldersFirst) foldersFirst().then(withDates) else withDates
        return rows.map(::Keyed).sortedWith(full.then(byName)).map { it.row }
    }

    private fun fieldComparator(field: SortField, byName: Comparator<Keyed>): Comparator<Keyed> = when (field) {
        SortField.NAME -> byName
        SortField.SIZE -> compareBy { it.row.totalSize }
        SortField.DATE -> compareBy { it.date ?: 0L }
        SortField.TYPE -> compareBy { it.type }
    }

    private fun foldersFirst(): Comparator<Keyed> = compareBy { !it.row.isFolder }

    private fun nullDatesLast(): Comparator<Keyed> = compareBy { it.date == null }
}
