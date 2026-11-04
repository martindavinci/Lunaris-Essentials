package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.feature.work.JobKind
import io.github.martindavinci.lunaris.pdf.feature.work.JobSession
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.pages.ContentImagePages
import io.github.martindavinci.lunaris.pdf.pages.ContentOutputFiles
import io.github.martindavinci.lunaris.pdf.pages.ImagePages
import io.github.martindavinci.lunaris.pdf.pages.OutputFiles
import io.github.martindavinci.lunaris.pdf.pages.PageEdits
import io.github.martindavinci.lunaris.pdf.pages.PagePictures
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.pages.PageThumbnails
import io.github.martindavinci.lunaris.pdf.pages.PdfOpening
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SplitPlan
import io.github.martindavinci.lunaris.pdf.pages.WorkChannel
import io.github.martindavinci.lunaris.pdf.pages.WorkEngine
import io.github.martindavinci.lunaris.pdf.pages.WorkFiles
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Takes its parts as interfaces, so tests need no file, no provider and no WebView. */
@HiltViewModel
class PagesViewModel internal constructor(
    private val saved: SavedStateHandle,
    private val current: CurrentDocument,
    private val pictures: PagePictures,
    private val images: ImagePages,
    private val files: OutputFiles,
    val channel: WorkChannel,
    runner: WorkRunner,
    private val openNew: suspend (String) -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        saved: SavedStateHandle,
        current: CurrentDocument,
        pictures: PageThumbnails,
        images: ContentImagePages,
        files: ContentOutputFiles,
        engine: WorkEngine,
        workFiles: WorkFiles,
        opener: DocumentOpener,
    ) : this(saved, current, pictures, images, files, engine, WorkRunner(engine, workFiles, workFiles::freeBytes), opener::openNew)

    private val documentUri: String = saved.get<String>(KEY_URI) ?: current.document.value?.uri.orEmpty()
    private val documentName: String = saved.get<String>(KEY_NAME) ?: current.document.value?.name.orEmpty()
    private val knownPages: Int = saved.get<Int>(KEY_PAGES) ?: current.forPages?.pages ?: 0
    private val locked: Boolean = saved.get<Boolean>(KEY_LOCKED) ?: current.forPages?.locked ?: false

    private val sources = PageSources.decode(saved.get<String>(KEY_SOURCES)) ?: PageSources(documentUri)
    private var edits = PageEdits.decode(saved.get<String>(KEY_EDITS).orEmpty()) ?: PageEdits(pagesOf(knownPages))
    private var lastTouched: Long? = null
    private var leaveWhenSaved = false
    private val restored = saved.get<String>(KEY_EDITS) != null
    private var sizeAtOpening: Long? = null
    private var lastTarget: String? = null

    private val mutableState = MutableStateFlow(PagesUiState(name = documentName, slots = edits.slots))
    val state: StateFlow<PagesUiState> = mutableState.asStateFlow()

    private val session = JobSession(viewModelScope, runner, files, ::onSaved) { work ->
        mutableState.update { it.copy(work = work) }
        markUnsaved()
    }
    private val adding = PagesAdding(pictures, images, sources) { change -> mutableState.update(change) }
    private val jobs = PagesJobs(viewModelScope, session, files, sources, { ui }) { change -> mutableState.update(change) }

    init {
        saved[KEY_URI] = documentUri
        saved[KEY_NAME] = documentName
        saved[KEY_PAGES] = knownPages
        saved[KEY_LOCKED] = locked
        publish()
        viewModelScope.launch { open() }
    }

    suspend fun thumbnailOf(slot: PageSlot): ImageBitmap? = when (val content = slot.content) {
        is SlotContent.PageOf -> pictures.page(sources.uriOf(content.source), content.index)
        is SlotContent.PictureOf -> pictures.picture(sources.uriOf(content.source))
        is SlotContent.Blank -> null
    }

    suspend fun pickThumbnail(pick: PagePick, index: Int): ImageBitmap? = pictures.page(sources.uriOf(pick.source), index)

    fun onAction(action: PagesAction) {
        when (action) {
            is PagesAction.ToggleSelected -> if (ui.canEdit) toggle(action.id)
            PagesAction.SelectAll -> mutableState.update { it.copy(selected = it.slots.map(PageSlot::id).toSet()) }
            PagesAction.ClearSelection -> mutableState.update { it.copy(selected = emptySet()) }
            PagesAction.RotateSelected -> changing { rotate(ui.selected) }
            PagesAction.DeleteSelected -> deleteSelected()
            is PagesAction.Move -> if (ui.canEdit) changing { move(action.ids, action.toIndex) }
            PagesAction.Undo -> changing { undo() }
            PagesAction.Redo -> changing { redo() }
            PagesAction.OpenAdd -> if (ui.canEdit) mutableState.update { it.copy(panel = PagesPanel.Add(defaultInsertAfter())) }
            PagesAction.OpenSplit -> if (ui.canEdit) mutableState.update { it.copy(panel = PagesPanel.Split(SplitPlan(listOf("")))) }
            PagesAction.ClosePanel -> mutableState.update { it.copy(panel = null) }
            is PagesAction.SetInsertAfter -> mutableState.update { it.copy(panel = PagesPanel.Add(action.after)) }
            PagesAction.AddBlank -> addBlank()
            is PagesAction.AddOtherPdf -> viewModelScope.launch { adding.chooseFrom(action.uri, insertPlace()) }
            is PagesAction.AddImages -> viewModelScope.launch { insert(insertPlace(), adding.images(action.uris)); keepSources() }
            is PagesAction.TogglePicked -> adding.toggle(action.index)
            PagesAction.PickAll -> adding.chooseAll()
            PagesAction.ConfirmPick -> ui.pick?.let { pick -> insert(pick.insertAfter, adding.confirm(pick)); keepSources() }
            PagesAction.CancelPick -> mutableState.update { it.copy(pick = null) }
            is PagesAction.SetSplitEveryPage -> jobs.splitting { it.copy(everyPage = action.on) }
            is PagesAction.SetSplitRow -> jobs.splitting { plan -> plan.copy(rows = plan.rows.mapIndexed { i, row -> if (i == action.row) action.text else row }) }
            PagesAction.AddSplitRow -> jobs.splitting { it.copy(rows = it.rows + "") }
            is PagesAction.RemoveSplitRow -> jobs.splitting { plan ->
                plan.copy(rows = plan.rows.filterIndexed { i, _ -> i != action.row }.ifEmpty { listOf("") })
            }
            is PagesAction.SaveTo -> {
                if (ui.askingToLeave) leaveWhenSaved = true
                lastTarget = action.uri
                jobs.save(action.uri, action.name)
            }
            is PagesAction.ExtractTo -> {
                lastTarget = action.uri
                jobs.extract(action.uri, action.name)
            }
            is PagesAction.SplitInto -> jobs.split(action.folder, documentName)
            PagesAction.RequestLeave -> requestLeave()
            PagesAction.KeepEditing -> mutableState.update { it.copy(askingToLeave = false) }
            PagesAction.DiscardAndLeave -> mutableState.update { it.copy(askingToLeave = false, close = true) }
            PagesAction.CancelWork -> session.cancel()
            PagesAction.RetryWork -> session.retry()
            PagesAction.CloseWorkFailure -> {
                leaveWhenSaved = false
                session.closeFailure()
            }
            PagesAction.CloseSaved -> {
                session.closeSaved()
                if (leaveWhenSaved) mutableState.update { it.copy(close = true) }
            }
            PagesAction.OpenSaved -> openSaved()
            PagesAction.NoticeShown -> mutableState.update { it.copy(notice = null) }
            PagesAction.ReaderOpened -> mutableState.update { it.copy(openReader = false) }
        }
    }

    private val ui get() = state.value

    private suspend fun open() {
        sizeAtOpening = files.describe(documentUri)?.bytes
        when (val opening = pictures.open(documentUri)) {
            is PdfOpening.Pages -> {
                if (locked) {
                    mutableState.update { it.copy(problem = PagesProblem.PROTECTED, loading = false) }
                    return
                }
                if (!restored && opening.count != edits.slots.size && edits.changes == 0) {
                    edits = PageEdits(pagesOf(opening.count))
                    publish()
                }
                mutableState.update { it.copy(loading = false) }
            }
            PdfOpening.Protected -> mutableState.update { it.copy(problem = PagesProblem.PROTECTED, loading = false) }
            PdfOpening.Damaged -> mutableState.update { it.copy(problem = PagesProblem.DAMAGED, loading = false, slots = emptyList()) }
        }
    }

    private fun toggle(id: Long) {
        lastTouched = id
        mutableState.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    }

    private fun deleteSelected() {
        if (!edits.delete(ui.selected)) {
            mutableState.update { it.copy(notice = PagesNotice.LAST_PAGE) }
            return
        }
        mutableState.update { it.copy(selected = emptySet()) }
        publish()
    }

    private fun addBlank() {
        val after = insertPlace()
        viewModelScope.launch {
            val neighbour = ui.slots.getOrNull(after - 1) ?: ui.slots.firstOrNull()
            insert(after, listOf(adding.blankLike(neighbour)))
        }
    }

    private fun insert(after: Int, contents: List<SlotContent>) {
        mutableState.update { it.copy(panel = null, pick = null) }
        if (contents.isEmpty()) return
        changing { insert(after, contents) }
    }

    private fun insertPlace(): Int = (ui.panel as? PagesPanel.Add)?.insertAfter ?: ui.pick?.insertAfter ?: ui.slots.size

    private fun defaultInsertAfter(): Int {
        val at = ui.slots.indexOfFirst { it.id == lastTouched }
        return if (at >= 0) at + 1 else ui.slots.size
    }

    private fun requestLeave() {
        when {
            ui.work is WorkState.Running -> Unit
            edits.changes > 0 -> mutableState.update { it.copy(askingToLeave = true) }
            else -> mutableState.update { it.copy(close = true) }
        }
    }

    /** The picker lets the user choose the document itself, and may call it by another uri: hence the size too. */
    private suspend fun onSaved(kind: JobKind) {
        if (kind == JobKind.SAVE) {
            edits.markSaved()
            publish()
        }
        if (kind != JobKind.SPLIT && (lastTarget == documentUri || files.describe(documentUri)?.bytes != sizeAtOpening)) {
            leaveWhenSaved = true
            mutableState.update { it.copy(originalReplaced = true) }
        }
    }

    private fun openSaved() {
        val done = ui.work as? WorkState.Saved ?: return
        viewModelScope.launch {
            openNew(done.uri)
            session.closeSaved()
            mutableState.update { it.copy(openReader = true) }
        }
    }

    private fun changing(change: PageEdits.() -> Unit) {
        edits.change()
        publish()
    }

    private fun publish() {
        val kept = edits.slots.map { it.id }.toSet()
        mutableState.update {
            it.copy(
                slots = edits.slots,
                selected = it.selected.intersect(kept),
                changes = edits.changes,
                canUndo = edits.canUndo,
                canRedo = edits.canRedo,
            )
        }
        val written = edits.encode()
        // Too long a text would kill the app when Android saves its state: then a dying process loses the edits.
        saved[KEY_EDITS] = written.takeIf { it.length <= MAX_SAVED_CHARS }
        keepSources()
        markUnsaved()
    }

    private fun keepSources() {
        saved[KEY_SOURCES] = sources.encode()
    }

    private fun markUnsaved() {
        current.hasUnsavedEdits = edits.changes > 0 || ui.work is WorkState.Running
    }

    private fun pagesOf(count: Int) = List(count) { PageSlot(it.toLong(), SlotContent.PageOf(0, it)) }

    override fun onCleared() {
        current.hasUnsavedEdits = false
        pictures.close()
    }

    private companion object {
        const val KEY_URI = "pages_uri"
        const val KEY_NAME = "pages_name"
        const val KEY_PAGES = "pages_count"
        const val KEY_LOCKED = "pages_locked"
        const val KEY_EDITS = "pages_edits"
        const val KEY_SOURCES = "pages_sources"
        const val MAX_SAVED_CHARS = 200_000
    }
}
