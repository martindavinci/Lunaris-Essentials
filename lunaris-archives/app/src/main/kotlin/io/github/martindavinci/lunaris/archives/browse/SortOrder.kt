package io.github.martindavinci.lunaris.archives.browse

enum class SortField { NAME, SIZE, DATE, TYPE }

data class SortOrder(val field: SortField = SortField.NAME, val ascending: Boolean = true, val foldersFirst: Boolean = true)
