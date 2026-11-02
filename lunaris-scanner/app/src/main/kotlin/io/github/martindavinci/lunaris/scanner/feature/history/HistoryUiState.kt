package io.github.martindavinci.lunaris.scanner.feature.history

/** [allItems] is the whole history, [visibleItems] the part that matches [query]. */
data class HistoryUiState(
    val loaded: Boolean = false,
    val allItems: List<HistoryItem> = emptyList(),
    val query: String = "",
    val keepHistory: Boolean = true,
    val confirmingClear: Boolean = false,
) {
    val visibleItems: List<HistoryItem> = allItems.matching(query)
}

sealed interface HistoryAction {
    data class QueryChanged(val query: String) : HistoryAction
    data class Open(val item: HistoryItem) : HistoryAction
    data class Delete(val id: Long) : HistoryAction
    data object RequestClear : HistoryAction
    data object DismissClear : HistoryAction
    data object ConfirmClear : HistoryAction
}
