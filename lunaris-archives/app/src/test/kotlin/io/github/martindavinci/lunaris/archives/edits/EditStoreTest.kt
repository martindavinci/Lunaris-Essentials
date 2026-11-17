package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { EditStore(folder.root) }

    @Test fun put_thenAll_roundTrips_inANewStore() {
        val r = record("a").copy(pending = Snapshot("/p/a", 3, 4, "ff"), archiveMtime = null)
        store.put(r)
        assertThat(EditStore(folder.root).all()).containsExactly(r)
    }
    @Test fun corruptFile_isIgnored_andReplacedOnNextPut() {
        File(folder.root, "edits").mkdirs(); File(folder.root, "edits/records.json").writeText("{not json")
        assertThat(store.all()).isEmpty()
        store.put(record("a"))
        assertThat(EditStore(folder.root).all().map { it.id }).containsExactly("a")
    }
    @Test fun put_upsertsByCopyPath_keepingPendingAndBaseline() {
        val first = record("a").copy(pending = Snapshot("/p/a", 3, 4, "ff"))
        store.put(first)
        store.put(record("b").copy(copyPath = first.copyPath, sha256 = "other"))
        val only = store.all().single()
        assertThat(only.id).isEqualTo("a"); assertThat(only.pending).isEqualTo(first.pending); assertThat(only.sha256).isEqualTo(first.sha256)
    }
    @Test fun overLimit_dropsOldestWithoutPending() {
        store.put(record("old-pending", created = 0).copy(pending = Snapshot("/p", 1, 1, "aa")))
        repeat(Limits.MAX_EDIT_RECORDS) { store.put(record("r$it", created = it + 1L)) }
        val ids = store.all().map { it.id }
        assertThat(ids).hasSize(Limits.MAX_EDIT_RECORDS)
        assertThat(ids).contains("old-pending"); assertThat(ids).doesNotContain("r0")
    }
    @Test fun update_transformsAndRemoves() {
        store.put(record("a")); store.put(record("b"))
        store.update { if (it.id == "a") null else it.copy(size = 99) }
        assertThat(store.all().map { it.id to it.size }).containsExactly("b" to 99L)
    }
    @Test fun changes_emitsAfterEveryWrite() {
        store.put(record("a"))
        assertThat(store.changes.value.map { it.id }).containsExactly("a")
        store.remove(listOf("a"))
        assertThat(store.changes.value).isEmpty()
    }
    @Test fun json_hasNoPasswordField() {
        store.put(record("a"))
        assertThat(File(folder.root, "edits/records.json").readText().lowercase()).doesNotContain("password")
    }

    private fun record(id: String, created: Long = 1) = EditRecord(
        id = id, copyPath = "/cache/open/k/0-1/$id.txt", archiveUri = "content://a/1", archiveKey = "0123456789abcdef",
        archiveName = "a.zip", archiveSize = 100, archiveMtime = 5, archiveFingerprint = "fp", archiveFormat = "ZIP",
        entryPath = "docs/$id.txt", size = 10, mtime = 20, sha256 = "00", updatable = true, createdMillis = created,
    )
}
