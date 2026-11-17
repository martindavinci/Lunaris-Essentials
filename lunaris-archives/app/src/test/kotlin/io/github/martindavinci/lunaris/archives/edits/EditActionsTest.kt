package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditActionsTest {
    @get:Rule val temp = TemporaryFolder()
    private val io = FileUpdateIo()
    private val cache by lazy { temp.newFolder("cache") }
    private val files by lazy { temp.newFolder("files") }
    private val store by lazy { EditStore(files) }
    private val revoked = mutableListOf<File>()
    private val watcher by lazy { EditWatcher(store, files, { revoked += it }, Dispatchers.Unconfined) }
    private val saved = mutableMapOf<String, String>()
    private val moves = mutableListOf<Pair<String, String>>()
    private val remembered = mutableMapOf<String, CharArray>()
    private val actions by lazy {
        EditActions(
            store, watcher, ZipUpdater(io, UpdateJournal(files), cache) { Long.MAX_VALUE }, io, { revoked += it },
            { key -> remembered[key]?.copyOf() },
            object : SaveIo {
                override fun write(target: String, from: File) { saved[target] = from.readText() }
                override fun createIn(tree: String, name: String, mime: String): String = "$tree/$name".also { saved[it] = "" }
            },
        ) { old, new, _ -> moves += old to new }
    }

    private val zip by lazy {
        File(temp.newFolder(), "a.zip").apply {
            ZipOutputStream(outputStream()).use { z ->
                listOf("a.md" to "one", "b.md" to "two").forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            }
        }
    }
    private val uri get() = "file:${zip.path}"

    private fun handOut(name: String): File {
        val facts = io.facts(uri)!!
        val copy = File(cache, "open/k/$name").apply { parentFile!!.mkdirs(); writeText(ZipFile(zip).use { z -> String(z.getInputStream(z.getEntry(name)).readBytes()) }) }
        store.put(EditRecord(
            "id-$name", copy.path, uri, "key", "a.zip", facts.size, facts.mtime, facts.fingerprint, "ZIP", name,
            copy.length(), copy.lastModified(), Fingerprint.sha256(copy), updatable = true, createdMillis = System.currentTimeMillis(),
            entryRaw = name.toByteArray().joinToString("") { "%02x".format(it) },
        ))
        return copy
    }
    private fun edit(copy: File, text: String) { copy.writeText(text); copy.setLastModified(copy.lastModified() + 5_000) }
    private fun content(name: String) = ZipFile(zip).use { z -> String(z.getInputStream(z.getEntry(name)).readBytes()) }

    @Test fun update_scansFirst_soTheLatestBytesGoIn() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "first edit"); watcher.scan()
        edit(a, "latest edit")
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
        assertThat(content("a.md")).isEqualTo("latest edit")
        assertThat(store.all()).isEmpty(); assertThat(revoked).contains(a)
    }
    @Test fun secondEdit_afterAnUpdate_isStillUpdatable() = runBlocking<Unit> {
        val a = handOut("a.md"); val b = handOut("b.md")
        edit(a, "A2"); watcher.scan()
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
        edit(b, "B2"); watcher.scan()
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
        assertThat(content("a.md")).isEqualTo("A2"); assertThat(content("b.md")).isEqualTo("B2")
    }
    @Test fun anEditMadeDuringTheUpdate_staysPending() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        io.onWrite = { edit(a, "A3 while writing"); io.onWrite = null }
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
        watcher.scan()
        assertThat(watcher.changed.value.single().entries.single().entryPath).isEqualTo("a.md")
    }
    @Test fun twoChangedCopiesOfOneEntry_theNewestGoesIn() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "older"); watcher.scan()
        val second = File(cache, "open/k2/a.md").apply { parentFile!!.mkdirs(); writeText("one") }
        val first = store.all().single()
        store.put(first.copy(id = "id-second", copyPath = second.path, size = 3, mtime = second.lastModified(), sha256 = Fingerprint.sha256(second), pending = null))
        second.writeText("newest"); second.setLastModified(a.lastModified() + 60_000); watcher.scan()
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
        assertThat(content("a.md")).isEqualTo("newest"); assertThat(store.all()).isEmpty()
    }
    @Test fun failedUpdate_refreshesTheRecords_soARetrySucceeds() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        io.failAfterBytes = 50
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Refused(UpdateResult.Failed))
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
    }
    @Test fun update_usesARememberedPassword() = runBlocking<Unit> {
        remembered["key"] = "pw".toCharArray()
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        assertThat(actions.update(uri, null)).isEqualTo(EditOutcome.Updated(uri))
    }
    @Test fun pickedFile_withAnotherFingerprint_doesNotMatch() = runBlocking<Unit> {
        handOut("a.md")
        val other = temp.newFile("other.zip").apply { writeText("not the same") }
        assertThat(actions.matchesArchive(uri, "file:${other.path}")).isFalse()
    }
    @Test fun pickedFile_matching_movesRecordsToTheNewUri() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        val same = File(temp.newFolder(), "same.zip").apply { writeBytes(zip.readBytes()); setLastModified(zip.lastModified()) }
        val picked = "file:${same.path}"
        assertThat(actions.matchesArchive(uri, picked)).isTrue()
        assertThat(actions.update(uri, null, writableUri = picked)).isEqualTo(EditOutcome.Updated(picked))
        assertThat(ZipFile(same).use { z -> String(z.getInputStream(z.getEntry("a.md")).readBytes()) }).isEqualTo("A2")
        assertThat(moves).containsExactly(uri to picked)
    }
    @Test fun ignore_dropsSnapshotsAndRevokes() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        val snapshot = File(store.all().single().pending!!.path)
        assertThat(actions.ignore(uri)).isEqualTo(EditOutcome.Ignored)
        assertThat(store.all()).isEmpty(); assertThat(snapshot.exists()).isFalse(); assertThat(a.exists()).isFalse()
        assertThat(revoked).contains(a); assertThat(content("a.md")).isEqualTo("one")
    }
    @Test fun saveOne_writesTheLatestEdit_andClosesTheCase() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan(); edit(a, "A3")
        assertThat(actions.saveOne(uri, "id-a.md", "content://out/a.md")).isEqualTo(EditOutcome.Saved)
        assertThat(saved["content://out/a.md"]).isEqualTo("A3"); assertThat(store.all()).isEmpty()
        assertThat(a.exists()).isFalse()   // never served again as if it were the entry
    }
    @Test fun saveOne_withASecondChange_savesTheOneAskedFor() = runBlocking<Unit> {
        val a = handOut("a.md"); edit(a, "A2"); watcher.scan()
        val b = handOut("b.md"); edit(b, "B2")
        assertThat(actions.saveOne(uri, "id-a.md", "content://out/a.md")).isEqualTo(EditOutcome.Saved)
        assertThat(saved["content://out/a.md"]).isEqualTo("A2"); assertThat(store.all().single().id).isEqualTo("id-b.md")
    }
    @Test fun saveAll_putsEveryChangedFileInTheFolder() = runBlocking<Unit> {
        val a = handOut("a.md"); val b = handOut("b.md"); edit(a, "A2"); edit(b, "B2"); watcher.scan()
        assertThat(actions.saveAll(uri, "content://tree")).isEqualTo(EditOutcome.Saved)
        assertThat(saved).containsExactly("content://tree/a.md", "A2", "content://tree/b.md", "B2")
    }
}
