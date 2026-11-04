package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.ui.graphics.ImageBitmap
import io.github.martindavinci.lunaris.pdf.convert.FileKind
import io.github.martindavinci.lunaris.pdf.convert.TextLayout
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.pages.PreparedSource

enum class FileProblem { PROTECTED, DAMAGED, UNSUPPORTED }

/** What can be told of a file as soon as it is chosen. [pages] is an estimate for text. */
data class FileFacts(val name: String, val kind: FileKind, val pages: Int, val problem: FileProblem? = null)

data class CreateItem(
    val id: Long,
    val uri: String,
    val name: String,
    val kind: FileKind,
    val pages: Int,
    val problem: FileProblem? = null,
)

/** A row just removed, for its Undo. */
data class RemovedItem(val item: CreateItem, val index: Int)

/** The files behind the list. An interface, so that tests need no provider. */
interface CreateSources {
    suspend fun inspect(uri: String): FileFacts

    /** Null if it can no longer be read. Text is laid out here, and comes back as a PDF. */
    suspend fun prepare(uri: String, kind: FileKind, layout: TextLayout): PreparedSource?
    suspend fun thumbnail(uri: String, kind: FileKind): ImageBitmap?
    fun close()
}

data class CreateUiState(
    val items: List<CreateItem> = emptyList(),
    val layout: TextLayout = TextLayout(),
    val layoutOpen: Boolean = false,
    val removed: RemovedItem? = null,
    val work: WorkState? = null,
    val askingToLeave: Boolean = false,
    val close: Boolean = false,
    val openReader: Boolean = false,
) {
    val unusable: Int get() = items.count { it.problem != null }
    val canCreate: Boolean get() = items.isNotEmpty() && unusable == 0
    val totalPages: Int get() = items.sumOf { it.pages }
    val hasText: Boolean get() = items.any { it.kind == FileKind.TEXT || it.kind == FileKind.MARKDOWN || it.kind == FileKind.HTML }
}

sealed interface CreateAction {
    data class AddFiles(val uris: List<String>) : CreateAction
    data class Remove(val id: Long) : CreateAction
    data class UndoRemove(val removed: RemovedItem) : CreateAction
    data object RemovedShown : CreateAction
    data class Move(val from: Int, val to: Int) : CreateAction
    data object OpenLayout : CreateAction
    data object CloseLayout : CreateAction
    data class SetLayout(val layout: TextLayout) : CreateAction
    data class CreateTo(val uri: String, val name: String) : CreateAction
    data object CancelWork : CreateAction
    data object RetryWork : CreateAction
    data object CloseWorkFailure : CreateAction
    data object RemoveFailedFile : CreateAction
    data object CloseSaved : CreateAction
    data object OpenSaved : CreateAction
    data object ReaderOpened : CreateAction
    data object RequestLeave : CreateAction
    data object KeepList : CreateAction
    data object LeaveList : CreateAction
}
