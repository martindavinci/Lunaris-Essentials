package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.convert.FileKind
import io.github.martindavinci.lunaris.pdf.convert.PageFormat
import io.github.martindavinci.lunaris.pdf.convert.PageMargins
import io.github.martindavinci.lunaris.pdf.convert.TextLayout
import io.github.martindavinci.lunaris.pdf.feature.work.JobKind
import io.github.martindavinci.lunaris.pdf.feature.work.JobPlan
import io.github.martindavinci.lunaris.pdf.feature.work.JobPlanFailure
import io.github.martindavinci.lunaris.pdf.feature.work.JobSession
import io.github.martindavinci.lunaris.pdf.feature.work.JobTarget
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.pages.ContentCreateSources
import io.github.martindavinci.lunaris.pdf.pages.ContentOutputFiles
import io.github.martindavinci.lunaris.pdf.pages.OutputFiles
import io.github.martindavinci.lunaris.pdf.pages.PageJob
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SourceKind
import io.github.martindavinci.lunaris.pdf.pages.WorkChannel
import io.github.martindavinci.lunaris.pdf.pages.WorkEngine
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.pages.WorkFiles
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import io.github.martindavinci.lunaris.pdf.session.CreateInbox
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Takes its parts as interfaces, so tests need no file, no provider and no WebView. */
@HiltViewModel
class CreateViewModel internal constructor(
    private val saved: SavedStateHandle,
    private val sources: CreateSources,
    files: OutputFiles,
    val channel: WorkChannel,
    runner: WorkRunner,
    inbox: CreateInbox,
    private val current: CurrentDocument,
    private val openNew: suspend (String) -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        saved: SavedStateHandle,
        sources: ContentCreateSources,
        files: ContentOutputFiles,
        engine: WorkEngine,
        workFiles: WorkFiles,
        inbox: CreateInbox,
        current: CurrentDocument,
        opener: DocumentOpener,
    ) : this(saved, sources, files, engine, WorkRunner(engine, workFiles, workFiles::freeBytes), inbox, current, opener::openNew)

    private val mutableState = MutableStateFlow(
        CreateUiState(
            items = CreateItemsCodec.decode(saved.get<String>(KEY_ITEMS).orEmpty()),
            layout = decodeLayout(saved.get<String>(KEY_LAYOUT)),
        ),
    )
    val state: StateFlow<CreateUiState> = mutableState.asStateFlow()
    private var nextId = (state.value.items.maxOfOrNull { it.id } ?: -1L) + 1

    private val session = JobSession(viewModelScope, runner, files, onSaved = {}) { work ->
        mutableState.update { it.copy(work = work) }
        current.hasUnsavedEdits = work is WorkState.Running
    }

    init {
        add(inbox.take())
    }

    suspend fun thumbnailOf(item: CreateItem): ImageBitmap? = if (item.problem != null) null else sources.thumbnail(item.uri, item.kind)

    fun onAction(action: CreateAction) {
        when (action) {
            is CreateAction.AddFiles -> add(action.uris)
            is CreateAction.Remove -> remove(action.id)
            is CreateAction.UndoRemove -> changeItems { list ->
                list.take(action.removed.index) + action.removed.item + list.drop(action.removed.index)
            }
            CreateAction.RemovedShown -> mutableState.update { it.copy(removed = null) }
            is CreateAction.Move -> changeItems { list -> list.toMutableList().apply { add(action.to, removeAt(action.from)) } }
            CreateAction.OpenLayout -> mutableState.update { it.copy(layoutOpen = true) }
            CreateAction.CloseLayout -> mutableState.update { it.copy(layoutOpen = false) }
            is CreateAction.SetLayout -> {
                mutableState.update { it.copy(layout = action.layout) }
                saved[KEY_LAYOUT] = encodeLayout(action.layout)
            }
            is CreateAction.CreateTo -> create(action.uri, action.name)
            CreateAction.CancelWork -> session.cancel()
            CreateAction.RetryWork -> session.retry()
            CreateAction.CloseWorkFailure -> session.closeFailure()
            CreateAction.RemoveFailedFile -> removeFailed()
            CreateAction.CloseSaved -> session.closeSaved()
            CreateAction.OpenSaved -> openSaved()
            CreateAction.ReaderOpened -> mutableState.update { it.copy(openReader = false) }
            CreateAction.RequestLeave -> mutableState.update {
                if (it.items.isEmpty()) it.copy(close = true) else it.copy(askingToLeave = true)
            }
            CreateAction.KeepList -> mutableState.update { it.copy(askingToLeave = false) }
            CreateAction.LeaveList -> mutableState.update { it.copy(askingToLeave = false, close = true) }
        }
    }

    private fun add(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = uris.map { uri ->
                val facts = sources.inspect(uri)
                CreateItem(nextId++, uri, facts.name, facts.kind, facts.pages, facts.problem)
            }
            changeItems { it + added }
        }
    }

    private fun remove(id: Long) {
        val index = state.value.items.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = state.value.items[index]
        changeItems { list -> list.filterNot { it.id == id } }
        mutableState.update { it.copy(removed = RemovedItem(item, index)) }
    }

    private fun changeItems(change: (List<CreateItem>) -> List<CreateItem>) {
        mutableState.update { it.copy(items = change(it.items)) }
        saved[KEY_ITEMS] = CreateItemsCodec.encode(state.value.items)
    }

    private fun create(uri: String, name: String) {
        if (state.value.work is WorkState.Failed) session.closeFailure()
        if (!state.value.canCreate) return
        session.start(JobKind.CREATE, listOf(JobTarget(uri, name))) { report ->
            val items = state.value.items
            val layout = state.value.layout
            val kinds = mutableListOf<SourceKind>()
            val uris = mutableListOf<String>()
            val pages = mutableListOf<PageSlot>()
            items.forEachIndexed { index, item ->
                report(index, items.size)
                val made = sources.prepare(item.uri, item.kind, layout) ?: throw JobPlanFailure(WorkFailure.UNREADABLE, index)
                kinds += made.kind
                uris += made.uri
                when (item.kind) {
                    FileKind.IMAGE -> pages += PageSlot(pages.size.toLong(), SlotContent.PictureOf(index, made.turn), made.turn)
                    else -> repeat(made.pages) { page -> pages += PageSlot(pages.size.toLong(), SlotContent.PageOf(index, page)) }
                }
            }
            JobPlan(PageJob(kinds, listOf(pages)), uris)
        }
    }

    private fun removeFailed() {
        val failed = state.value.work as? WorkState.Failed
        val at = failed?.source
        session.closeFailure()
        if (at != null) state.value.items.getOrNull(at)?.let { remove(it.id) }
    }

    private fun openSaved() {
        val done = state.value.work as? WorkState.Saved ?: return
        viewModelScope.launch {
            openNew(done.uri)
            session.closeSaved()
            mutableState.update { it.copy(openReader = true) }
        }
    }

    override fun onCleared() {
        current.hasUnsavedEdits = false
        sources.close()
    }

    private fun encodeLayout(layout: TextLayout) = "${layout.format.name}|${layout.landscape}|${layout.margins.name}"

    private fun decodeLayout(text: String?): TextLayout {
        val parts = text?.split('|')?.takeIf { it.size == LAYOUT_FIELDS } ?: return TextLayout()
        return TextLayout(
            format = PageFormat.entries.firstOrNull { it.name == parts[0] } ?: PageFormat.A4,
            landscape = parts[1].toBoolean(),
            margins = PageMargins.entries.firstOrNull { it.name == parts[2] } ?: PageMargins.NORMAL,
        )
    }

    private companion object {
        const val KEY_ITEMS = "create_items"
        const val KEY_LAYOUT = "create_layout"
        const val LAYOUT_FIELDS = 3
    }
}
