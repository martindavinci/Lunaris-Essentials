package io.github.martindavinci.lunaris.pdf.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsListTest {
    private fun entry(n: Int, page: Int = 1) = RecentEntry("content://a/$n", "$n.pdf", page, "page-width", n.toLong())

    @Test fun newDocument_goesOnTop() {
        val list = RecentsList.touch(listOf(entry(1)), "content://a/2", "2.pdf", nowMillis = 50)
        assertThat(list.map { it.uri }).containsExactly("content://a/2", "content://a/1").inOrder()
        assertThat(list.first().page).isEqualTo(1)
        assertThat(list.first().lastOpenedMillis).isEqualTo(50)
    }

    @Test fun touching_movesToTheTopWithoutLosingThePage() {
        val list = RecentsList.touch(listOf(entry(1), entry(2, page = 7)), "content://a/2", "renamed.pdf", nowMillis = 50)
        assertThat(list.map { it.uri }).containsExactly("content://a/2", "content://a/1").inOrder()
        assertThat(list.first().page).isEqualTo(7)
        assertThat(list.first().name).isEqualTo("renamed.pdf")
        assertThat(list.first().lastOpenedMillis).isEqualTo(50)
    }

    @Test fun listIsCappedAt50() {
        val full = List(RecentsList.MAX_ENTRIES) { entry(it) }
        val list = RecentsList.touch(full, "content://a/new", "new.pdf", nowMillis = 1)
        assertThat(list).hasSize(50)
        assertThat(list.first().uri).isEqualTo("content://a/new")
        assertThat(list.map { it.uri }).doesNotContain(full.last().uri)
    }

    @Test fun position_isStoredWithoutReordering() {
        val list = RecentsList.savePosition(listOf(entry(1), entry(2)), "content://a/2", page = 9, scale = "2")
        assertThat(list.map { it.uri }).containsExactly("content://a/1", "content://a/2").inOrder()
        assertThat(list.last().page).isEqualTo(9)
        assertThat(list.last().scale).isEqualTo("2")
    }

    @Test fun positionOfAnUnknownDocument_changesNothing() {
        val list = listOf(entry(1))
        assertThat(RecentsList.savePosition(list, "content://a/9", page = 9, scale = "2")).isEqualTo(list)
    }

    @Test fun remove_leavesTheOthers() {
        assertThat(RecentsList.remove(listOf(entry(1), entry(2)), "content://a/1").map { it.uri })
            .containsExactly("content://a/2")
    }
}
