package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.browse.SearchHit
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.ui.CrumbSegment

/**
 * Everything the browser screen shows. [title] and [badge] are known before the header (the
 * password dialog shows them); the selection bar acts on entries of every folder through
 * [BrowserAction.ShareSelection] and [BrowserAction.ExtractSelection].
 */
data class BrowserUiState(
    val title: String = "",
    val badge: String = "",
    val header: ArchiveHeader? = null,
    val loading: LoadState = LoadState.Reading,
    val path: List<String> = emptyList(),
    val crumbs: List<CrumbSegment> = emptyList(),
    val rows: List<FolderRow> = emptyList(),
    val hiddenUnsafe: Int = 0,
    val capped: Boolean = false,
    val partial: Boolean = false,
    val sort: SortOrder = SortOrder(),
    val query: String? = null,
    val hits: List<SearchHit> = emptyList(),
    val selection: Set<Long>? = null,
    val selectedFolders: Set<List<String>> = emptySet(),
    val canShareSelection: Boolean = false,
    val opening: OpeningState? = null,
    val password: PasswordPrompt? = null,
    val problem: ArchiveProblem? = null,
    val sheet: BrowserSheet? = null,
    val confirmLarge: String? = null,
    val notice: BrowserNotice? = null,
    val launch: LaunchRequest? = null,
    val exit: Boolean = false,
) {
    val selecting: Boolean get() = selection != null
    val selectedCount: Int get() = (selection?.size ?: 0) + selectedFolders.size

    fun isSelected(row: FolderRow): Boolean =
        if (row.isFolder) row.path in selectedFolders else row.entry?.index?.let { it in selection.orEmpty() } == true
}

data class ArchiveHeader(
    val id: String, val name: String, val formatLabel: String, val fileCount: Int, val folderCount: Int,
    val sizeBytes: Long, val packedBytes: Long?, val encryption: Encryption, val createdBy: HostOs?, val comment: String?,
)

sealed interface LoadState {
    data object Reading : LoadState
    data class ReadingSlow(val items: Int) : LoadState
    data class Copying(val done: Long, val total: Long?) : LoadState
    data object Ready : LoadState
}

/** [fraction] below zero: the size is not known, the bar is indeterminate. */
data class OpeningState(val name: String, val fraction: Float)

/** What the password is for: opening the archive, one entry, or extracting protected entries. */
enum class PasswordTarget { ARCHIVE, ENTRY, EXTRACT }
data class PasswordPrompt(val name: String, val target: PasswordTarget, val wrong: Boolean = false, val checking: Boolean = false)

sealed interface BrowserSheet {
    data class Row(val row: FolderRow) : BrowserSheet
    data object Info : BrowserSheet
    data object Sort : BrowserSheet
    data class NoApp(val row: FolderRow) : BrowserSheet
    data object Rar4 : BrowserSheet
}

sealed interface BrowserNotice {
    data class Damaged(val name: String) : BrowserNotice
    data object TooDeep : BrowserNotice
    data object NoSpace : BrowserNotice
    data object Changed : BrowserNotice
    data object ShareFailed : BrowserNotice
    /** The open archive cannot be read any more: [name] is the archive's. */
    data class Gone(val name: String) : BrowserNotice
}

sealed interface LaunchRequest {
    data class View(val uri: String, val mime: String, val writable: Boolean = false) : LaunchRequest
    data class Chooser(val uri: String, val mime: String, val writable: Boolean = false) : LaunchRequest
    data class Send(val uris: List<String>, val mime: String) : LaunchRequest
    data class Extract(val jobArgs: ExtractArgs) : LaunchRequest
    data class Nested(val requestId: String) : LaunchRequest
}

class ExtractArgs(val archiveId: String, val indices: LongArray) {
    override fun equals(other: Any?): Boolean = other is ExtractArgs && archiveId == other.archiveId && indices.contentEquals(other.indices)
    override fun hashCode(): Int = java.util.Objects.hash(archiveId, indices.contentHashCode())
}

sealed interface BrowserAction {
    data class OpenRow(val row: FolderRow) : BrowserAction
    data object Up : BrowserAction
    data class GoToCrumb(val index: Int) : BrowserAction
    data class LongPress(val row: FolderRow) : BrowserAction
    data class Toggle(val row: FolderRow) : BrowserAction
    data object SelectAll : BrowserAction
    data object ClearSelection : BrowserAction
    data object ShareSelection : BrowserAction
    data object ExtractSelection : BrowserAction
    data class ShowRowSheet(val row: FolderRow) : BrowserAction
    data class OpenWith(val row: FolderRow) : BrowserAction
    data class Share(val rows: List<FolderRow>) : BrowserAction
    data class ExtractRows(val rows: List<FolderRow>) : BrowserAction
    data object ExtractAll : BrowserAction
    data object CancelOpening : BrowserAction
    data object OpenSearch : BrowserAction
    data class SetQuery(val text: String) : BrowserAction
    data object CloseSearch : BrowserAction
    /** [password] is wiped by the view model; it never enters the state. Identity equality is intended. */
    class SubmitPassword(val password: CharArray, val remember: Boolean) : BrowserAction
    data object PasswordEdited : BrowserAction
    data object DismissPassword : BrowserAction
    data object ShowInfo : BrowserAction
    data object ShowSort : BrowserAction
    data class SetSort(val order: SortOrder) : BrowserAction
    data object ShareArchive : BrowserAction
    data object DismissSheet : BrowserAction
    data object ContinueLarge : BrowserAction
    data object StopLarge : BrowserAction
    data class LaunchHandled(val ok: Boolean) : BrowserAction
    data object NoticeShown : BrowserAction
    data object ShowPartial : BrowserAction
}
