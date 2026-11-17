package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditWatcherTest {
    @get:Rule val temp = TemporaryFolder()
    private val cache by lazy { temp.newFolder("cache") }
    private val files by lazy { temp.newFolder("files") }
    private val store by lazy { EditStore(files) }
    private val revoked = mutableListOf<File>()
    private val watcher by lazy { EditWatcher(store, files, { revoked += it }, Dispatchers.Unconfined) }
    private val now = 100L * HOUR

    /** A copy handed out with [text], and its record. */
    private fun handedOut(text: String = "v1", id: String = "a", archive: String = "content://a/1"): File {
        val copy = File(cache, "open/k/0-1/$id.txt").apply { parentFile!!.mkdirs(); writeText(text); setLastModified(now - HOUR) }
        store.put(EditRecord(
            id, copy.path, archive, "key", "a.zip", 100, 5, "fp", "ZIP", "docs/$id.txt",
            copy.length(), copy.lastModified(), Fingerprint.sha256(copy), updatable = true, createdMillis = now - HOUR,
        ))
        return copy
    }

    private fun edit(copy: File, text: String, at: Long) { copy.writeText(text); copy.setLastModified(at) }

    @Test fun unchanged_staysQuiet() = runBlocking<Unit> {
        handedOut(); watcher.scan(now)
        assertThat(store.all().single().pending).isNull(); assertThat(watcher.changed.value).isEmpty()
    }
    @Test fun touchedButSameBytes_isNotAChange() = runBlocking<Unit> {
        val copy = handedOut(); copy.setLastModified(now)
        watcher.scan(now)
        assertThat(store.all().single().pending).isNull(); assertThat(store.all().single().mtime).isEqualTo(now)
    }
    @Test fun changed_takesASnapshot_andShowsInChanged() = runBlocking<Unit> {
        val copy = handedOut(); edit(copy, "v2 longer", now)
        watcher.scan(now)
        val pending = store.all().single().pending!!
        assertThat(File(pending.path).readText()).isEqualTo("v2 longer")
        assertThat(File(pending.path).startsWith(files)).isTrue()
        assertThat(copy.exists()).isTrue()   // the live copy stays where the other app writes
        val changed = watcher.changed.value.single()
        assertThat(changed.archiveUri).isEqualTo("content://a/1"); assertThat(changed.entries.map { it.id }).containsExactly("a")
    }
    @Test fun autosaveAfterSnapshot_latestWins() = runBlocking<Unit> {
        val copy = handedOut(); edit(copy, "v2", now)
        watcher.scan(now)
        edit(copy, "v3 after autosave", now + MINUTE)
        watcher.scan(now + MINUTE)
        assertThat(File(store.all().single().pending!!.path).readText()).isEqualTo("v3 after autosave")
    }
    @Test fun copyGone_withSnapshot_keepsTheEdit() = runBlocking<Unit> {
        val copy = handedOut(); edit(copy, "v2", now)
        watcher.scan(now)
        copy.delete()
        watcher.scan(now + MINUTE)
        assertThat(File(store.all().single().pending!!.path).readText()).isEqualTo("v2")
    }
    @Test fun copyGone_withoutChange_dropsTheRecord() = runBlocking<Unit> {
        handedOut().delete()
        watcher.scan(now)
        assertThat(store.all()).isEmpty()
    }
    @Test fun oldUnchanged_isDropped_andRevoked() = runBlocking<Unit> {
        val copy = handedOut()
        watcher.scan(now + Limits.TEMP_MAX_AGE_MILLIS + HOUR)
        assertThat(store.all()).isEmpty(); assertThat(revoked).containsExactly(copy)
    }
    @Test fun protectedFiles_areTheLiveCopies() = runBlocking<Unit> {
        val a = handedOut(id = "a"); val b = handedOut(id = "b")
        assertThat(watcher.files()).containsExactly(a, b)
    }
    @Test fun orphanSnapshots_areRemoved() = runBlocking<Unit> {
        val orphan = File(files, "edits/pending/gone/x.txt").apply { parentFile!!.mkdirs(); writeText("x") }
        watcher.scan(now)
        assertThat(orphan.parentFile!!.exists()).isFalse()
    }
    @Test fun check_returnsTheRecordAfterAScanOfItAlone() = runBlocking<Unit> {
        val copy = handedOut(); edit(copy, "v2", now)
        val checked = watcher.check(store.all().single(), now)
        assertThat(checked!!.pending).isNotNull()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
