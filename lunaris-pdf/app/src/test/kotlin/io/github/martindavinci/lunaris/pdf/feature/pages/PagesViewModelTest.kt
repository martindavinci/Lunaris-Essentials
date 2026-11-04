package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.feature.work.FakeOutputFiles
import io.github.martindavinci.lunaris.pdf.feature.work.FakeWorkChannel
import io.github.martindavinci.lunaris.pdf.feature.work.FakeWorkFiles
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import io.github.martindavinci.lunaris.pdf.pages.ImagePages
import io.github.martindavinci.lunaris.pdf.pages.PagePictures
import io.github.martindavinci.lunaris.pdf.pages.PageSize
import io.github.martindavinci.lunaris.pdf.pages.PdfOpening
import io.github.martindavinci.lunaris.pdf.pages.PreparedImage
import io.github.martindavinci.lunaris.pdf.pages.SlotContent
import io.github.martindavinci.lunaris.pdf.pages.SourceKind
import io.github.martindavinci.lunaris.pdf.pages.WorkCommand
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import io.github.martindavinci.lunaris.pdf.session.ForPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagesViewModelTest {
    private class FakePictures(var opening: (String) -> PdfOpening = { PdfOpening.Pages(5) }) : PagePictures {
        override suspend fun open(uri: String) = opening(uri)
        override suspend fun page(uri: String, index: Int): ImageBitmap? = null
        override suspend fun size(uri: String, index: Int) = PageSize(100f, 200f)
        override suspend fun picture(uri: String): ImageBitmap? = null
        override fun close() = Unit
    }

    private val channel = FakeWorkChannel()
    private val files = FakeOutputFiles()
    private val pictures = FakePictures()
    private val current = CurrentDocument().apply {
        set(OpenDocument("content://doc", "Moon.pdf"))
        forPages = ForPages(pages = 5, locked = false)
    }
    private val images = ImagePages { uri -> if ("bad" in uri) null else PreparedImage(uri, SourceKind.JPEG, 90) }
    private var opened: String? = null

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = PagesViewModel(
        saved,
        current,
        pictures,
        images,
        files,
        channel,
        WorkRunner(channel, FakeWorkFiles(), freeBytes = { Long.MAX_VALUE }),
        openNew = { opened = it },
    )

    private fun PagesViewModel.act(vararg actions: PagesAction) = actions.forEach(::onAction)

    private val PagesViewModel.ui get() = state.value

    private fun PagesViewModel.ids() = ui.slots.map { it.id }

    /** The job the page was asked to run: for each output, its entries. */
    private fun startedJob(): JSONObject = JSONObject((channel.sent.last { it is WorkCommand.Start } as WorkCommand.Start).job)

    @Test
    fun `the pages of the document fill the grid, and loading ends`() {
        val vm = viewModel()
        assertThat(vm.ids()).hasSize(5)
        assertThat(vm.ui.slots[2].content).isEqualTo(SlotContent.PageOf(0, 2))
        assertThat(vm.ui.loading).isFalse()
        assertThat(vm.ui.name).isEqualTo("Moon.pdf")
    }

    @Test
    fun `a protected source shows every page and allows nothing`() {
        pictures.opening = { PdfOpening.Protected }
        val vm = viewModel()
        assertThat(vm.ui.problem).isEqualTo(PagesProblem.PROTECTED)
        assertThat(vm.ids()).hasSize(5)
        vm.act(PagesAction.ToggleSelected(0), PagesAction.Move(setOf(0), 3), PagesAction.OpenAdd, PagesAction.OpenSplit)
        assertThat(vm.ui.selected).isEmpty()
        assertThat(vm.ui.panel).isNull()
        assertThat(vm.ui.changes).isEqualTo(0)
    }

    @Test
    fun `a file opened with a password is protected, whatever the renderer says`() {
        current.forPages = ForPages(pages = 5, locked = true)
        assertThat(viewModel().ui.problem).isEqualTo(PagesProblem.PROTECTED)
    }

    @Test
    fun `a damaged source shows no grid`() {
        pictures.opening = { PdfOpening.Damaged }
        val vm = viewModel()
        assertThat(vm.ui.problem).isEqualTo(PagesProblem.DAMAGED)
        assertThat(vm.ui.slots).isEmpty()
    }

    @Test
    fun `selecting, then rotating and deleting what is selected`() {
        val vm = viewModel()
        vm.act(PagesAction.ToggleSelected(1), PagesAction.ToggleSelected(3), PagesAction.RotateSelected)
        assertThat(vm.ui.slots[1].rotation).isEqualTo(90)
        assertThat(vm.ui.changes).isEqualTo(1)
        assertThat(vm.ui.selected).containsExactly(1L, 3L)
        vm.act(PagesAction.DeleteSelected)
        assertThat(vm.ids()).containsExactly(0L, 2L, 4L).inOrder()
        assertThat(vm.ui.selected).isEmpty()
        vm.act(PagesAction.Undo)
        assertThat(vm.ids()).hasSize(5)
        vm.act(PagesAction.Redo)
        assertThat(vm.ids()).hasSize(3)
    }

    @Test
    fun `select all and clear`() {
        val vm = viewModel()
        vm.act(PagesAction.SelectAll)
        assertThat(vm.ui.selected).hasSize(5)
        vm.act(PagesAction.ClearSelection)
        assertThat(vm.ui.selecting).isFalse()
    }

    @Test
    fun `deleting all says why it cannot`() {
        val vm = viewModel()
        vm.act(PagesAction.SelectAll, PagesAction.DeleteSelected)
        assertThat(vm.ids()).hasSize(5)
        assertThat(vm.ui.notice).isEqualTo(PagesNotice.LAST_PAGE)
    }

    @Test
    fun `moving a group`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0, 2), 5))
        assertThat(vm.ids()).containsExactly(1L, 3L, 4L, 0L, 2L).inOrder()
    }

    @Test
    fun `saving runs one output with the pages as they stand, and makes that the new start`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        assertThat(vm.ui.work).isInstanceOf(WorkState.Running::class.java)
        channel.report(WorkEvent.Ready)
        val job = startedJob()
        assertThat(job.getJSONArray("outputs").length()).isEqualTo(1)
        assertThat(job.getJSONArray("outputs").getJSONArray(0).getJSONObject(0).getInt("index")).isEqualTo(1)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        assertThat(vm.ui.work).isInstanceOf(WorkState.Saved::class.java)
        assertThat(vm.ui.changes).isEqualTo(0)
        assertThat(vm.ui.canUndo).isTrue()
    }

    @Test
    fun `extracting takes only what is selected, in the order on screen, and leaves the changes alone`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(3), 0), PagesAction.ToggleSelected(1), PagesAction.ToggleSelected(3))
        vm.act(PagesAction.ExtractTo("content://out/x.pdf", "x.pdf"))
        channel.report(WorkEvent.Ready)
        val pages = startedJob().getJSONArray("outputs").getJSONArray(0)
        assertThat(pages.length()).isEqualTo(2)
        assertThat(pages.getJSONObject(0).getInt("index")).isEqualTo(3)
        assertThat(pages.getJSONObject(1).getInt("index")).isEqualTo(1)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        assertThat(vm.ui.changes).isEqualTo(1)
    }

    @Test
    fun `splitting makes one file per row in the chosen folder`() {
        val vm = viewModel()
        vm.act(
            PagesAction.OpenSplit,
            PagesAction.SetSplitRow(0, "1-2"),
            PagesAction.AddSplitRow,
            PagesAction.SetSplitRow(1, "3-"),
            PagesAction.SplitInto("tree://folder"),
        )
        assertThat(vm.ui.panel).isNull()
        channel.report(WorkEvent.Ready)
        assertThat(startedJob().getJSONArray("outputs").length()).isEqualTo(2)
        assertThat(files.created).containsExactly("Moon (1-2).pdf", "Moon (3-).pdf").inOrder()
    }

    @Test
    fun `a split with a bad row does not start`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenSplit, PagesAction.SetSplitRow(0, "99"), PagesAction.SplitInto("tree://folder"))
        assertThat(vm.ui.work).isNull()
        assertThat(files.created).isEmpty()
        assertThat(vm.ui.panel).isNotNull()
    }

    @Test
    fun `every page on its own`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenSplit, PagesAction.SetSplitEveryPage(true), PagesAction.SplitInto("tree://folder"))
        channel.report(WorkEvent.Ready)
        assertThat(startedJob().getJSONArray("outputs").length()).isEqualTo(5)
    }

    @Test
    fun `the row of the split form grows and shrinks, but keeps one`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenSplit, PagesAction.AddSplitRow, PagesAction.RemoveSplitRow(0), PagesAction.RemoveSplitRow(0))
        assertThat((vm.ui.panel as PagesPanel.Split).plan.rows).hasSize(1)
    }

    @Test
    fun `new pages go after the last page touched, unless told otherwise`() {
        val vm = viewModel()
        vm.act(PagesAction.ToggleSelected(2), PagesAction.ToggleSelected(2), PagesAction.OpenAdd)
        assertThat((vm.ui.panel as PagesPanel.Add).insertAfter).isEqualTo(3)
        vm.act(PagesAction.SetInsertAfter(1), PagesAction.AddBlank)
        assertThat(vm.ui.slots[1].content).isEqualTo(SlotContent.Blank(100f, 200f))
        assertThat(vm.ui.panel).isNull()
    }

    @Test
    fun `with nothing touched they go at the end`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenAdd)
        assertThat((vm.ui.panel as PagesPanel.Add).insertAfter).isEqualTo(5)
    }

    @Test
    fun `pages of another document are chosen, then land where asked`() {
        val vm = viewModel()
        pictures.opening = { PdfOpening.Pages(3) }
        vm.act(PagesAction.OpenAdd, PagesAction.SetInsertAfter(1), PagesAction.AddOtherPdf("content://other"))
        val pick = vm.ui.pick!!
        assertThat(pick.pageCount).isEqualTo(3)
        assertThat(pick.chosen).containsExactly(0, 1, 2)
        vm.act(PagesAction.TogglePicked(1), PagesAction.ConfirmPick)
        assertThat(vm.ui.pick).isNull()
        assertThat(vm.ui.slots.map { it.content }.subList(1, 3)).containsExactly(SlotContent.PageOf(1, 0), SlotContent.PageOf(1, 2)).inOrder()
    }

    @Test
    fun `another document with a password is refused, with its message`() {
        val vm = viewModel()
        pictures.opening = { PdfOpening.Protected }
        vm.act(PagesAction.OpenAdd, PagesAction.AddOtherPdf("content://other"))
        assertThat(vm.ui.pick).isNull()
        assertThat(vm.ui.notice).isEqualTo(PagesNotice.OTHER_PROTECTED)
    }

    @Test
    fun `images become pages turned as they should be, and the job knows their kind`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenAdd, PagesAction.AddImages(listOf("content://a.jpg", "content://b.jpg")))
        assertThat(vm.ui.slots).hasSize(7)
        assertThat(vm.ui.slots[5].content).isEqualTo(SlotContent.PictureOf(1, 90))
        assertThat(vm.ui.slots[5].rotation).isEqualTo(90)
        vm.act(PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        channel.report(WorkEvent.Ready)
        assertThat(startedJob().getJSONArray("sources").getString(1)).isEqualTo("jpeg")
    }

    @Test
    fun `an image that cannot be read says so, and the others still come`() {
        val vm = viewModel()
        vm.act(PagesAction.OpenAdd, PagesAction.AddImages(listOf("content://bad", "content://b.jpg")))
        assertThat(vm.ui.slots).hasSize(6)
        assertThat(vm.ui.notice).isEqualTo(PagesNotice.IMAGE_FAILED)
    }

    @Test
    fun `leaving with changes asks once, without changes it leaves`() {
        val vm = viewModel()
        vm.act(PagesAction.RequestLeave)
        assertThat(vm.ui.close).isTrue()

        val changed = viewModel()
        changed.act(PagesAction.Move(setOf(0), 5), PagesAction.RequestLeave)
        assertThat(changed.ui.askingToLeave).isTrue()
        assertThat(changed.ui.close).isFalse()
        changed.act(PagesAction.KeepEditing)
        assertThat(changed.ui.askingToLeave).isFalse()
        changed.act(PagesAction.RequestLeave, PagesAction.DiscardAndLeave)
        assertThat(changed.ui.close).isTrue()
    }

    @Test
    fun `saving on the way out leaves when the sheet closes`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.RequestLeave, PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        assertThat(vm.ui.askingToLeave).isFalse()
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        assertThat(vm.ui.close).isFalse()
        vm.act(PagesAction.CloseSaved)
        assertThat(vm.ui.close).isTrue()
    }

    @Test
    fun `a job that fails keeps the edits`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Failed(WorkFailure.MEMORY, null))
        assertThat(vm.ui.work).isInstanceOf(WorkState.Failed::class.java)
        assertThat(vm.ui.changes).isEqualTo(1)
        vm.act(PagesAction.CloseWorkFailure)
        assertThat(vm.ui.work).isNull()
        assertThat(files.deleted).containsExactly("content://out/a.pdf")
    }

    @Test
    fun `cancelling takes the empty target away and keeps the edits`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        channel.report(WorkEvent.Ready)
        vm.act(PagesAction.CancelWork)
        assertThat(vm.ui.work).isNull()
        assertThat(vm.ui.changes).isEqualTo(1)
        assertThat(files.deleted).containsExactly("content://out/a.pdf")
    }

    @Test
    fun `open puts the new file in the Reader`() {
        val vm = viewModel()
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        vm.act(PagesAction.OpenSaved)
        assertThat(opened).isEqualTo("content://out/a.pdf")
        assertThat(vm.ui.openReader).isTrue()
    }

    @Test
    fun `edits survive the process, and the document with them`() {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.act(PagesAction.Move(setOf(0), 5), PagesAction.AddImages(listOf("content://a.jpg")))
        current.set(null)
        current.forPages = null
        val back = viewModel(saved)
        assertThat(back.ids()).isEqualTo(vm.ids())
        assertThat(back.ui.changes).isEqualTo(vm.ui.changes)
        assertThat(back.ui.name).isEqualTo("Moon.pdf")
        back.act(PagesAction.SaveTo("content://out/a.pdf", "a.pdf"))
        channel.report(WorkEvent.Ready)
        assertThat(startedJob().getJSONArray("sources").length()).isEqualTo(2)
    }

    @Test
    fun `while there is something to lose, a new document is held back`() {
        val vm = viewModel()
        assertThat(current.hasUnsavedEdits).isFalse()
        vm.act(PagesAction.Move(setOf(0), 5))
        assertThat(current.hasUnsavedEdits).isTrue()
        vm.act(PagesAction.Undo)
        assertThat(current.hasUnsavedEdits).isFalse()
    }
}
