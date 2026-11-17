package io.github.martindavinci.lunaris.archives.feature.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.edits.EditActions
import io.github.martindavinci.lunaris.archives.edits.EditRecord
import io.github.martindavinci.lunaris.archives.edits.EditStore
import io.github.martindavinci.lunaris.archives.edits.EditWatcher
import io.github.martindavinci.lunaris.archives.edits.EditsRunner
import io.github.martindavinci.lunaris.archives.edits.FileUpdateIo
import io.github.martindavinci.lunaris.archives.edits.Fingerprint
import io.github.martindavinci.lunaris.archives.edits.RecoveryOutcome
import io.github.martindavinci.lunaris.archives.edits.SaveIo
import io.github.martindavinci.lunaris.archives.edits.UpdateJournal
import io.github.martindavinci.lunaris.archives.edits.ZipUpdater
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.lingala.zip4j.io.outputstream.ZipOutputStream as Zip4jOut
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditsViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val io = FileUpdateIo()
    private val cache by lazy { temp.newFolder("cache") }
    private val files by lazy { temp.newFolder("files") }
    private val store by lazy { EditStore(files) }
    private val watcher by lazy { EditWatcher(store, files, {}, Dispatchers.Unconfined) }
    private val journal by lazy { UpdateJournal(files) }
    private val actions by lazy {
        EditActions(store, watcher, ZipUpdater(io, journal, cache) { Long.MAX_VALUE }, io, {}, { null },
            object : SaveIo {
                override fun write(target: String, from: File) = Unit
                override fun createIn(tree: String, name: String, mime: String) = "$tree/$name"
            }) { _, _, _ -> }
    }
    private val runner by lazy { EditsRunner(actions, Dispatchers.Unconfined) }
    /** Every view model goes in [models], closed after the test: a live one would touch Dispatchers.Main once it is reset. */
    private val models = ViewModelStore()
    private var made = 0
    private fun viewModel(): EditsViewModel = ViewModelProvider.create(
        models, viewModelFactory { initializer { EditsViewModel(watcher, runner, actions, journal) } },
    )["vm${made++}", EditsViewModel::class]

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() {
        eventually { runner.working.value == null }   // a decision still on its way would land after the reset
        models.clear()
        Dispatchers.resetMain()
    }

    private fun zip(encrypted: Boolean = false): File = File(temp.newFolder(), "Trip.zip").apply {
        if (encrypted) Zip4jOut(outputStream(), "pw".toCharArray()).use { z ->
            z.putNextEntry(ZipParameters().apply { fileNameInZip = "a.md"; isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES })
            z.write("one".toByteArray()); z.closeEntry()
        } else ZipOutputStream(outputStream()).use { z -> z.putNextEntry(ZipEntry("a.md")); z.write("one".toByteArray()); z.closeEntry() }
    }

    /** [zip]'s "a.md" handed out and edited. */
    private fun edited(zip: File, updatable: Boolean = true): String {
        val uri = "file:${zip.path}"
        val facts = io.facts(uri)!!
        val copy = File(cache, "open/${zip.name}/a.md").apply { parentFile!!.mkdirs(); writeText("one") }
        store.put(EditRecord("id-${zip.parentFile!!.name}", copy.path, uri, "key", zip.name, facts.size, facts.mtime, facts.fingerprint, "ZIP", "a.md",
            3, copy.lastModified(), Fingerprint.sha256(copy), updatable, createdMillis = System.currentTimeMillis(), entryRaw = "612e6d64"))
        copy.writeText("edited"); copy.setLastModified(copy.lastModified() + 5_000)
        runBlocking { watcher.scan() }
        return uri
    }

    /** Decisions run on IO threads: waits until [done] holds. */
    private fun eventually(done: () -> Boolean) = runBlocking {
        kotlinx.coroutines.withTimeout(TIMEOUT) { while (!done()) kotlinx.coroutines.delay(POLL) }
    }

    @Test fun aChangedFile_showsInItsArchive() {
        val uri = edited(zip())
        val archive = viewModel().state.value.archives.single()
        assertThat(archive.archiveUri).isEqualTo(uri); assertThat(archive.entries.single().entryPath).isEqualTo("a.md")
    }
    @Test fun ignore_asksFirst_thenDropsTheChange() {
        val uri = edited(zip()); val vm = viewModel()
        vm.onAction(EditsAction.Ignore(uri))
        assertThat(vm.state.value.confirmIgnore).isEqualTo(uri); assertThat(store.all()).isNotEmpty()
        vm.onAction(EditsAction.ConfirmIgnore); eventually { store.all().isEmpty() }
        assertThat(store.all()).isEmpty(); assertThat(vm.state.value.archives).isEmpty()
    }
    @Test fun update_notWritable_asksToPickTheArchive() {
        val uri = edited(zip()); io.writable = false; val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.pick != null }
        assertThat(vm.state.value.pick?.archiveUri).isEqualTo(uri)
    }
    @Test fun pickingAnotherFile_saysSo() {
        val uri = edited(zip()); io.writable = false; val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.pick != null }
        val other = temp.newFile("other.zip").apply { writeText("x") }
        vm.onAction(EditsAction.Picked(uri, "file:${other.path}")); eventually { vm.state.value.message != null }
        assertThat(vm.state.value.message).isEqualTo(EditsMessage.DifferentFile)
    }
    @Test fun anArchivePickedAgain_isTheOneTheBrowserReloads() {
        val uri = edited(zip()); io.writable = false; val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.pick != null }
        val same = File(temp.newFolder(), "Trip.zip").apply { writeBytes(File(uri.removePrefix("file:")).readBytes()) }
        io.writable = true
        vm.onAction(EditsAction.Picked(uri, "file:${same.path}")); eventually { vm.state.value.reload != null }
        assertThat(vm.state.value.reload).isEqualTo(EditsReload(uri, "file:${same.path}"))
    }
    @Test fun aPasswordAfterPickingTheArchiveAgain_updatesThePickedFile() {
        val uri = edited(zip(encrypted = true)); io.readOnly += uri; val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.pick != null }
        val same = File(temp.newFolder(), "Trip.zip").apply { writeBytes(File(uri.removePrefix("file:")).readBytes()) }
        vm.onAction(EditsAction.Picked(uri, "file:${same.path}")); eventually { vm.state.value.password != null }
        vm.onAction(EditsAction.SubmitPassword("pw".toCharArray())); eventually { vm.state.value.message != null }
        assertThat(vm.state.value.message).isEqualTo(EditsMessage.Updated("Trip.zip"))
    }
    @Test fun update_needsPassword_asksForIt_andWipesIt() {
        val uri = edited(zip(encrypted = true)); val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.password != null }
        assertThat(vm.state.value.password?.archiveUri).isEqualTo(uri)
        val typed = "pw".toCharArray()
        vm.onAction(EditsAction.SubmitPassword(typed)); eventually { vm.state.value.message != null }
        assertThat(typed.all { it == Char(0) }).isTrue()
        assertThat(vm.state.value.message).isEqualTo(EditsMessage.Updated("Trip.zip"))
    }
    @Test fun updated_showsDone_andAsksTheBrowserToReload() {
        val uri = edited(zip()); val vm = viewModel()
        vm.onAction(EditsAction.Update(uri)); eventually { vm.state.value.message != null }
        assertThat(vm.state.value.message).isEqualTo(EditsMessage.Updated("Trip.zip"))
        assertThat(vm.state.value.reload).isEqualTo(EditsReload(uri, uri))
        vm.onAction(EditsAction.Reloaded); vm.onAction(EditsAction.MessageShown)
        assertThat(vm.state.value.reload).isNull(); assertThat(vm.state.value.message).isNull()
    }
    /** Home stays alive under the browser with its own view model: the result goes to the screen that asked. */
    @Test fun theResult_goesToTheScreenThatStartedTheUpdate() {
        val uri = edited(zip())
        val home = viewModel(); val browser = viewModel()
        browser.onAction(EditsAction.Update(uri)); eventually { browser.state.value.message != null }
        assertThat(browser.state.value.reload).isEqualTo(EditsReload(uri, uri))
        assertThat(home.state.value.message).isNull(); assertThat(home.state.value.reload).isNull()
    }
    @Test fun aStuckBackup_isShown_andDiscardedOnlyAfterConfirming() {
        val backup = File(journal.folder("u1").apply { mkdirs() }, "backup.zip").apply { writeText("x") }
        journal.begin("u1", "file:/a.zip", "a.zip", backup, 1, "fp", 1, "new"); journal.restoreFailed("u1")
        val vm = viewModel()
        assertThat(vm.state.value.stuck.single().name).isEqualTo("a.zip")
        vm.onAction(EditsAction.DiscardBackup("u1"))
        assertThat(backup.exists()).isTrue(); assertThat(vm.state.value.confirmDiscard).isEqualTo("u1")
        vm.onAction(EditsAction.ConfirmDiscard); eventually { vm.state.value.stuck.isEmpty() }
        assertThat(backup.exists()).isFalse()
    }
    @Test fun anArchivePutBackAtStartUp_isSaid() {
        val vm = viewModel()
        journal.recover(io)   // nothing to recover: no message
        assertThat(vm.state.value.message).isNull()
        assertThat(EditsMessage.of(RecoveryOutcome.Restored("Trip.zip", "u"))).isEqualTo(EditsMessage.Restored("Trip.zip"))
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val POLL = 10L
    }
}
