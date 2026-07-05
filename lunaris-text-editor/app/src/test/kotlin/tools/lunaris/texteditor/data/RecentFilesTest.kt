package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentFilesTest {

    @Test
    fun touch_addsToFront() {
        val list = RecentFiles.touch(emptyList(), RecentEntry("content://1", "a.txt"))
        assertThat(list.map { it.uri }).containsExactly("content://1")
    }

    @Test
    fun touch_movesExistingToFront_noDuplicate() {
        val start = listOf(RecentEntry("content://1", "a"), RecentEntry("content://2", "b"))
        val after = RecentFiles.touch(start, RecentEntry("content://2", "b"))
        assertThat(after.map { it.uri }).containsExactly("content://2", "content://1").inOrder()
    }

    @Test
    fun touch_capsAtMax() {
        var list = emptyList<RecentEntry>()
        repeat(60) { list = RecentFiles.touch(list, RecentEntry("content://$it", "f$it")) }
        assertThat(list.size).isEqualTo(RecentFiles.MAX)
        assertThat(list.first().uri).isEqualTo("content://59")
    }

    @Test
    fun remove_dropsEntry() {
        val start = listOf(RecentEntry("content://1", "a"), RecentEntry("content://2", "b"))
        assertThat(RecentFiles.remove(start, "content://1").map { it.uri }).containsExactly("content://2")
    }

    @Test
    fun jsonRoundTrip() {
        val start = listOf(RecentEntry("content://1", "a.txt"), RecentEntry("content://2", "b.kt"))
        val restored = RecentFiles.fromJson(RecentFiles.toJson(start))
        assertThat(restored).isEqualTo(start)
    }

    @Test
    fun fromJson_blank_isEmpty() {
        assertThat(RecentFiles.fromJson("")).isEmpty()
    }

    @Test fun toJson_fromJson_roundTripsTimestamp() {
        val list = listOf(RecentEntry("u1", "a.md", 1234L), RecentEntry("u2", "b.js", 5678L))
        assertThat(RecentFiles.fromJson(RecentFiles.toJson(list))).isEqualTo(list)
    }

    @Test fun fromJson_legacyWithoutTimestamp_defaultsToZero() {
        val legacy = """[{"uri":"u1","name":"a.md"}]"""
        assertThat(RecentFiles.fromJson(legacy)).containsExactly(RecentEntry("u1", "a.md", 0L))
    }
}
