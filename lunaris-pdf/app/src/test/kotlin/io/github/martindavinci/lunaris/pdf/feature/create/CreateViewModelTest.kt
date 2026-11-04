package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.pdf.convert.FileKind
import io.github.martindavinci.lunaris.pdf.convert.PageFormat
import io.github.martindavinci.lunaris.pdf.convert.TextLayout
import io.github.martindavinci.lunaris.pdf.feature.work.FakeOutputFiles
import io.github.martindavinci.lunaris.pdf.feature.work.FakeWorkChannel
import io.github.martindavinci.lunaris.pdf.feature.work.FakeWorkFiles
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.pages.PreparedSource
import io.github.martindavinci.lunaris.pdf.pages.SourceKind
import io.github.martindavinci.lunaris.pdf.pages.WorkCommand
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import io.github.martindavinci.lunaris.pdf.pages.WorkFailure
import io.github.martindavinci.lunaris.pdf.pages.WorkRunner
import io.github.martindavinci.lunaris.pdf.session.CreateInbox
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import kotlinx.coroutines.CompletableDeferred
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
class CreateViewModelTest {
    private class FakeSources : CreateSources {
        val layouts = mutableListOf<TextLayout>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun inspect(uri: String): FileFacts = when {
            uri.endsWith(".pdf") -> FileFacts(uri.substringAfterLast('/'), FileKind.PDF, 4)
            uri.endsWith(".jpg") -> FileFacts(uri.substringAfterLast('/'), FileKind.IMAGE, 1)
            uri.endsWith(".md") -> FileFacts(uri.substringAfterLast('/'), FileKind.MARKDOWN, 3)
            uri.endsWith(".lock") -> FileFacts("locked.pdf", FileKind.PDF, 0, FileProblem.PROTECTED)
            else -> FileFacts(uri.substringAfterLast('/'), FileKind.UNSUPPORTED, 0, FileProblem.UNSUPPORTED)
        }

        override suspend fun prepare(uri: String, kind: FileKind, layout: TextLayout): PreparedSource? {
            layouts += layout
            gate?.await()
            return when (kind) {
                FileKind.PDF -> PreparedSource(uri, SourceKind.PDF, 0, 4)
                FileKind.IMAGE -> PreparedSource(uri, SourceKind.JPEG, 90, 1)
                FileKind.MARKDOWN -> if ("gone" in uri) null else PreparedSource("file://made", SourceKind.PDF, 0, 2)
                else -> null
            }
        }

        override suspend fun thumbnail(uri: String, kind: FileKind): ImageBitmap? = null
        override fun close() = Unit
    }

    private val channel = FakeWorkChannel()
    private val files = FakeOutputFiles()
    private val sources = FakeSources()
    private val inbox = CreateInbox()
    private val current = CurrentDocument()
    private var opened: String? = null

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = CreateViewModel(
        saved,
        sources,
        files,
        channel,
        WorkRunner(channel, FakeWorkFiles(), freeBytes = { Long.MAX_VALUE }),
        inbox,
        current,
        openNew = { opened = it },
    )

    private val CreateViewModel.ui get() = state.value

    private fun CreateViewModel.names() = ui.items.map { it.name }

    private fun CreateViewModel.add(vararg uris: String) = onAction(CreateAction.AddFiles(uris.toList()))

    private fun startedJob() = JSONObject((channel.sent.last { it is WorkCommand.Start } as WorkCommand.Start).job)

    @Test
    fun `files join the list in the order chosen, and pages add up`() {
        val vm = viewModel()
        assertThat(vm.ui.canCreate).isFalse()
        vm.add("c://a.pdf", "c://b.jpg", "c://c.md")
        assertThat(vm.names()).containsExactly("a.pdf", "b.jpg", "c.md").inOrder()
        assertThat(vm.ui.totalPages).isEqualTo(8)
        assertThat(vm.ui.canCreate).isTrue()
    }

    @Test
    fun `an empty list cannot create`() {
        val vm = viewModel()
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        assertThat(vm.ui.work).isNull()
    }

    @Test
    fun `a file that cannot be used stays, with its reason, and holds Create back until it goes`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://x.lock", "c://Budget.xlsx")
        assertThat(vm.ui.items.map { it.problem }).containsExactly(null, FileProblem.PROTECTED, FileProblem.UNSUPPORTED).inOrder()
        assertThat(vm.ui.unusable).isEqualTo(2)
        assertThat(vm.ui.canCreate).isFalse()
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        assertThat(vm.ui.work).isNull()
        vm.ui.items.filter { it.problem != null }.forEach { vm.onAction(CreateAction.Remove(it.id)) }
        assertThat(vm.ui.canCreate).isTrue()
    }

    @Test
    fun `removing can be undone, to the same place`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://b.jpg", "c://c.md")
        vm.onAction(CreateAction.Remove(vm.ui.items[1].id))
        assertThat(vm.names()).containsExactly("a.pdf", "c.md").inOrder()
        val removed = vm.ui.removed!!
        vm.onAction(CreateAction.UndoRemove(removed))
        assertThat(vm.names()).containsExactly("a.pdf", "b.jpg", "c.md").inOrder()
    }

    @Test
    fun `rows move`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://b.jpg", "c://c.md")
        vm.onAction(CreateAction.Move(0, 2))
        assertThat(vm.names()).containsExactly("b.jpg", "c.md", "a.pdf").inOrder()
    }

    @Test
    fun `the job holds every page of every file, in the order of the list`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://b.jpg", "c://c.md")
        vm.onAction(CreateAction.Move(1, 0))
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        channel.report(WorkEvent.Ready)
        val job = startedJob()
        assertThat(job.getJSONArray("sources").getString(0)).isEqualTo("jpeg")
        val pages = job.getJSONArray("outputs").getJSONArray(0)
        assertThat(pages.length()).isEqualTo(1 + 4 + 2)
        assertThat(pages.getJSONObject(0).getInt("turn")).isEqualTo(90)
        assertThat(pages.getJSONObject(0).getInt("source")).isEqualTo(0)
        assertThat(pages.getJSONObject(4).getInt("source")).isEqualTo(1)
        assertThat(pages.getJSONObject(4).getInt("index")).isEqualTo(3)
        assertThat(pages.getJSONObject(5).getInt("source")).isEqualTo(2)
        assertThat(pages.getJSONObject(5).getInt("index")).isEqualTo(0)
    }

    @Test
    fun `the layout chosen reaches the text files`() {
        val vm = viewModel()
        vm.add("c://c.md")
        val letter = TextLayout(format = PageFormat.LETTER, landscape = true)
        vm.onAction(CreateAction.SetLayout(letter))
        assertThat(vm.ui.layout).isEqualTo(letter)
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        assertThat(sources.layouts).containsExactly(letter)
    }

    @Test
    fun `while preparing it says which file it is on`() {
        val vm = viewModel()
        sources.gate = CompletableDeferred()
        vm.add("c://a.pdf", "c://b.jpg")
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        val running = vm.ui.work as WorkState.Running
        assertThat(running.preparing).isEqualTo(0)
        assertThat(running.preparingOf).isEqualTo(2)
        sources.gate?.complete(Unit)
    }

    @Test
    fun `a file that went missing names itself, keeps the list, and can be taken out`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://gone.md")
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        val failed = vm.ui.work as WorkState.Failed
        assertThat(failed.failure).isEqualTo(WorkFailure.UNREADABLE)
        assertThat(failed.source).isEqualTo(1)
        assertThat(vm.names()).hasSize(2)
        vm.onAction(CreateAction.RemoveFailedFile)
        assertThat(vm.names()).containsExactly("a.pdf")
        assertThat(vm.ui.work).isNull()
    }

    @Test
    fun `the page's own complaint names the file too`() {
        val vm = viewModel()
        vm.add("c://a.pdf", "c://b.pdf")
        vm.onAction(CreateAction.CreateTo("c://out", "out.pdf"))
        channel.report(WorkEvent.Ready)
        channel.report(WorkEvent.Failed(WorkFailure.PROTECTED, 1))
        assertThat((vm.ui.work as WorkState.Failed).source).isEqualTo(1)
        vm.onAction(CreateAction.RemoveFailedFile)
        assertThat(vm.names()).containsExactly("a.pdf")
    }

    @Test
    fun `done, it can open, and the list is gone with the screen`() {
        val vm = viewModel()
        vm.add("c://a.pdf")
        vm.onAction(CreateAction.CreateTo("c://out/x.pdf", "x.pdf"))
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        assertThat(vm.ui.work).isInstanceOf(WorkState.Saved::class.java)
        assertThat(files.remembered).containsExactly("c://out/x.pdf" to 4)
        vm.onAction(CreateAction.OpenSaved)
        assertThat(opened).isEqualTo("c://out/x.pdf")
        assertThat(vm.ui.openReader).isTrue()
    }

    @Test
    fun `done closes the sheet and keeps the list for another try`() {
        val vm = viewModel()
        vm.add("c://a.pdf")
        vm.onAction(CreateAction.CreateTo("c://out/x.pdf", "x.pdf"))
        channel.report(WorkEvent.Ready)
        channel.makeOutput(0)
        channel.report(WorkEvent.Done)
        vm.onAction(CreateAction.CloseSaved)
        assertThat(vm.ui.work).isNull()
        assertThat(vm.names()).hasSize(1)
    }

    @Test
    fun `cancelling keeps the list and leaves no file`() {
        val vm = viewModel()
        vm.add("c://a.pdf")
        vm.onAction(CreateAction.CreateTo("c://out/x.pdf", "x.pdf"))
        channel.report(WorkEvent.Ready)
        vm.onAction(CreateAction.CancelWork)
        assertThat(vm.ui.work).isNull()
        assertThat(vm.names()).hasSize(1)
        assertThat(files.deleted).containsExactly("c://out/x.pdf")
    }

    @Test
    fun `leaving a list asks, an empty one just goes`() {
        val empty = viewModel()
        empty.onAction(CreateAction.RequestLeave)
        assertThat(empty.ui.close).isTrue()

        val vm = viewModel()
        vm.add("c://a.pdf")
        vm.onAction(CreateAction.RequestLeave)
        assertThat(vm.ui.askingToLeave).isTrue()
        vm.onAction(CreateAction.KeepList)
        assertThat(vm.ui.askingToLeave).isFalse()
        vm.onAction(CreateAction.RequestLeave)
        vm.onAction(CreateAction.LeaveList)
        assertThat(vm.ui.close).isTrue()
    }

    @Test
    fun `what was shared to the app is already in the list`() {
        inbox.put(listOf("c://a.jpg", "c://b.jpg"))
        val vm = viewModel()
        assertThat(vm.names()).containsExactly("a.jpg", "b.jpg").inOrder()
        assertThat(inbox.take()).isEmpty()
    }

    @Test
    fun `the list survives the process`() {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.add("c://a.pdf", "c://Budget.xlsx")
        val letter = TextLayout(format = PageFormat.LETTER)
        vm.onAction(CreateAction.SetLayout(letter))
        val back = viewModel(saved)
        assertThat(back.ui.items.map { it.uri }).containsExactly("c://a.pdf", "c://Budget.xlsx").inOrder()
        assertThat(back.ui.items[1].problem).isEqualTo(FileProblem.UNSUPPORTED)
        assertThat(back.ui.layout).isEqualTo(letter)
    }
}
