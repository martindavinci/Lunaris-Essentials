package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheJanitorTest {
    @get:Rule val temp = TemporaryFolder()
    private val hour = 60L * 60 * 1000
    private val now = 100 * hour
    private fun child(folder: String, name: String, age: Long, bytes: Int = 1): File =
        temp.root.resolve(folder).resolve(name).apply {
            mkdirs(); resolve("f").writeBytes(ByteArray(bytes)); setLastModified(now - age)
        }

    @Test fun sweep_dropsOldChildrenOnly() {
        val old = listOf("open", "share", "incoming").map { child(it, "old", 25 * hour) }
        val young = listOf("open", "share", "incoming").map { child(it, "young", 1 * hour) }
        val index = child("index", "old", 99 * hour)
        CacheJanitor(temp.root).sweep(now)
        assertThat(old.none { it.exists() }).isTrue()
        assertThat(young.all { it.exists() }).isTrue()
        assertThat(index.exists()).isTrue()
    }
    @Test fun sweepAndClear_skipProtectedCopies_atTheirRealDepth() {
        val kept = temp.root.resolve("open/key/0-1/a.txt").apply { parentFile!!.mkdirs(); writeText("edited"); setLastModified(now - 50 * hour) }
        val other = temp.root.resolve("open/key/1-2/b.txt").apply { parentFile!!.mkdirs(); writeText("b") }
        listOf(kept.parentFile!!, other.parentFile!!, kept.parentFile!!.parentFile!!).forEach { it.setLastModified(now - 50 * hour) }
        val janitor = CacheJanitor(temp.root) { setOf(kept) }
        janitor.sweep(now)
        assertThat(kept.exists()).isTrue(); assertThat(other.exists()).isFalse()
        janitor.clearAll()
        assertThat(kept.exists()).isTrue()
    }
    @Test fun workFolders_ofCreateAndUpdate_areSwept() {
        val work = listOf("create", "update").map { child(it, "old", 25 * hour) }
        CacheJanitor(temp.root).sweep(now)
        assertThat(work.none { it.exists() }).isTrue()
    }
    @Test fun clearAllAndSize() {
        listOf("open", "share", "incoming", "index").forEach { child(it, "x", 0, bytes = 5) }
        val janitor = CacheJanitor(temp.root)
        assertThat(janitor.sizeBytes()).isEqualTo(20)
        janitor.clearAll()
        assertThat(janitor.sizeBytes()).isEqualTo(0)
    }
}
