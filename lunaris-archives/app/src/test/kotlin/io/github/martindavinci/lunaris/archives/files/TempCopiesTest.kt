package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.entry
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder

class TempCopiesTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = "0123456789abcdef"
    private val hour = 60L * 60 * 1000

    @Test fun target_isPerArchiveAndEntry() {
        val copies = TempCopies(temp.root)
        val file = copies.target(key, entry(7, "docs/a:b.md"))
        assertThat(file.name).isEqualTo("a_b.md")
        assertThat(file.parentFile!!.name).isEqualTo("7-7")
        assertThat(file.parentFile!!.parentFile).isEqualTo(temp.root.resolve("open").resolve(key))
    }
    @Test fun withoutCrc_theDateNamesTheFolder() {
        val file = TempCopies(temp.root).target(key, entry(3, "a.md", mtime = 42).copy(crc = null))
        assertThat(file.parentFile!!.name).isEqualTo("3-t42")
    }
    @Test fun skippedEntries_areRefused() {
        assertThrows(IllegalArgumentException::class.java) { TempCopies(temp.root).target(key, entry(1, "../../x")) }
    }
    @Test fun existingCopy_onlyWhenTheSizeMatches() {
        val copies = TempCopies(temp.root)
        val e = entry(1, "a.md", size = 3)
        assertThat(copies.existingCopy(key, e)).isNull()
        copies.target(key, e).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(2)) }
        assertThat(copies.existingCopy(key, e)).isNull()
        copies.target(key, e).writeBytes(ByteArray(3))
        assertThat(copies.existingCopy(key, e)).isEqualTo(copies.target(key, e))
    }
    @Test fun trim_skipsProtectedCopies() {
        val now = 100 * hour
        lateinit var edited: File
        val copies = TempCopies(temp.root, capBytes = 1) { setOf(edited) }
        edited = copies.target(key, entry(1, "e.md")).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(6)); setLastModified(now - 30 * hour) }
        copies.trim(emptyList(), now)
        assertThat(edited.exists()).isTrue()
        copies.clearAll()
        assertThat(edited.exists()).isTrue()
    }
    @Test fun publish_neverReplacesAProtectedCopy() {
        lateinit var edited: File
        val copies = TempCopies(temp.root) { setOf(edited) }
        val e = entry(1, "e.md", size = 3)
        edited = copies.target(key, e).apply { parentFile!!.mkdirs(); writeText("edited text") }
        val attempt = copies.attempt(edited).apply { mkdirs(); resolve("e.md").writeBytes(ByteArray(3)) }
        assertThat(copies.publish(attempt, edited, e)).isEqualTo(edited)
        assertThat(edited.readText()).isEqualTo("edited text"); assertThat(attempt.exists()).isFalse()
    }
    @Test fun trim_dropsOldCopiesThenTheOldestBeyondTheCap() {
        val copies = TempCopies(temp.root, capBytes = 10)
        val now = 100 * hour
        fun made(i: Long, bytes: Int, age: Long) = copies.target(key, entry(i, "f$i")).apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)); setLastModified(now - age)
        }
        val stale = made(1, 1, 25 * hour)
        val oldest = made(2, 6, 3 * hour)
        val keep = made(3, 6, 4 * hour)
        val newest = made(4, 3, 1 * hour)
        copies.trim(listOf(keep), now)
        assertThat(stale.exists()).isFalse()
        assertThat(oldest.exists()).isFalse()
        assertThat(keep.exists()).isTrue()
        assertThat(newest.exists()).isTrue()
    }
    @Test fun trim_keepsEveryFileOfOneShare_evenBeyondTheCap() {
        val copies = TempCopies(temp.root, capBytes = 10)
        val now = 100 * hour
        val shared = (1L..3L).map { i ->
            copies.target(key, entry(i, "s$i")).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(6)); setLastModified(now - i * hour) }
        }
        copies.trim(shared, now)
        assertThat(shared.all { it.exists() }).isTrue()
    }
}
