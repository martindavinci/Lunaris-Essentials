package io.github.martindavinci.lunaris.pdf.feature.pages

import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SplitPlan

enum class PagesProblem { PROTECTED, DAMAGED }

enum class PagesNotice { LAST_PAGE, OTHER_PROTECTED, OTHER_DAMAGED, IMAGE_FAILED, FOLDER_FAILED }

sealed interface PagesPanel {
    /** [insertAfter]: 0 is the start, n is after page n. */
    data class Add(val insertAfter: Int) : PagesPanel
    data class Split(val plan: SplitPlan) : PagesPanel
}

/** Pages of another document, being chosen in the same grid. [source] is its place among the sources. */
data class PagePick(val source: Int, val pageCount: Int, val chosen: Set<Int>, val insertAfter: Int)

data class PagesUiState(
    val name: String = "",
    val slots: List<PageSlot> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val changes: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val loading: Boolean = true,
    val problem: PagesProblem? = null,
    val panel: PagesPanel? = null,
    val pick: PagePick? = null,
    val askingToLeave: Boolean = false,
    val work: WorkState? = null,
    val notice: PagesNotice? = null,
    /** The chosen target was the document itself: this grid no longer tells what is in it. */
    val originalReplaced: Boolean = false,
    /** Leave this screen once this is true. */
    val close: Boolean = false,
    /** The saved file is ready in the Reader. */
    val openReader: Boolean = false,
) {
    val canEdit: Boolean get() = problem == null
    val canSave: Boolean get() = canEdit && changes > 0
    val selecting: Boolean get() = selected.isNotEmpty()
}

sealed interface PagesAction {
    data class ToggleSelected(val id: Long) : PagesAction
    data object SelectAll : PagesAction
    data object ClearSelection : PagesAction
    data object RotateSelected : PagesAction
    data object DeleteSelected : PagesAction
    data class Move(val ids: Set<Long>, val toIndex: Int) : PagesAction
    data object Undo : PagesAction
    data object Redo : PagesAction

    data object OpenAdd : PagesAction
    data object OpenSplit : PagesAction
    data object ClosePanel : PagesAction
    data class SetInsertAfter(val after: Int) : PagesAction
    data object AddBlank : PagesAction
    data class AddOtherPdf(val uri: String) : PagesAction
    data class AddImages(val uris: List<String>) : PagesAction
    data class TogglePicked(val index: Int) : PagesAction
    data object PickAll : PagesAction
    data object ConfirmPick : PagesAction
    data object CancelPick : PagesAction

    data class SetSplitEveryPage(val on: Boolean) : PagesAction
    data class SetSplitRow(val row: Int, val text: String) : PagesAction
    data object AddSplitRow : PagesAction
    data class RemoveSplitRow(val row: Int) : PagesAction

    data class SaveTo(val uri: String, val name: String) : PagesAction
    data class ExtractTo(val uri: String, val name: String) : PagesAction
    data class SplitInto(val folder: String) : PagesAction

    data object RequestLeave : PagesAction
    data object KeepEditing : PagesAction
    data object DiscardAndLeave : PagesAction

    data object CancelWork : PagesAction
    data object RetryWork : PagesAction
    data object CloseWorkFailure : PagesAction
    data object CloseSaved : PagesAction
    data object OpenSaved : PagesAction
    data object NoticeShown : PagesAction
    data object ReaderOpened : PagesAction
}
