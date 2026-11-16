package io.github.martindavinci.lunaris.archives.feature.home

import io.github.martindavinci.lunaris.archives.recents.RecentArchive

/** Home: recents, search, the menu of a row, and one-shot messages and moves. */
data class HomeUiState(
    val entries: List<RecentArchive> = emptyList(),
    val continueItem: RecentArchive? = null,
    val query: String? = null,
    val rememberRecents: Boolean = true,
    val confirmClear: Boolean = false,
    val menuFor: String? = null,
    val notice: HomeNotice? = null,
    val navigate: HomeNav? = null,
)

sealed interface HomeNotice {
    data class Removed(val item: RecentArchive) : HomeNotice
    data object Gone : HomeNotice
    data object NoPicker : HomeNotice
    data object ShareFailed : HomeNotice
    data object ExtractionRecovered : HomeNotice
}

sealed interface HomeNav {
    data class Browser(val requestId: String) : HomeNav
    data object Create : HomeNav
    data object Settings : HomeNav
    data class ExtractAll(val requestId: String) : HomeNav
}

sealed interface HomeAction {
    data class OpenPicked(val uri: String) : HomeAction
    data class OpenRecent(val item: RecentArchive) : HomeAction
    data object OpenSearch : HomeAction
    data object CloseSearch : HomeAction
    data class SetQuery(val text: String) : HomeAction
    data class ShowMenu(val uri: String) : HomeAction
    data object HideMenu : HomeAction
    data class Remove(val item: RecentArchive) : HomeAction
    data object UndoRemove : HomeAction
    data object AskClear : HomeAction
    data object ConfirmClear : HomeAction
    data object DismissClear : HomeAction
    data class Share(val item: RecentArchive) : HomeAction
    data class ExtractAll(val item: RecentArchive) : HomeAction
    data object NoticeShown : HomeAction
    data object Navigated : HomeAction
}
