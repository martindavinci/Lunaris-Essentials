package io.github.martindavinci.lunaris.pdf.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsListTest {
    private fun entry(n: Int, page: Int = 1) = RecentEntry("content://a/$n", "$n.pdf", page, "page-width", n.toLong())

    @Test fun newDocument_goesOnTop() {
        val list = RecentsList.touch(listOf(entry(1)), RecentVisit("content://a/2", "2.pdf"), nowMillis = 50)
        assertThat(list.map { it.uri }).containsExactly("content://a/2", "content://a/1").inOrder()
        assertThat(list.first().page).isEqualTo(1)
        assertThat(list.first().lastOpenedMillis).isEqualTo(50)
    }

    @Test fun touching_movesToTheTopWithoutLosingThePage() {
        val list = RecentsList.touch(listOf(entry(1), entry(2, page = 7)), RecentVisit("content://a/2", "renamed.pdf"), nowMillis = 50)
        assertThat(list.map { it.uri }).containsExactly("content://a/2", "content://a/1").inOrder()
        assertThat(list.first().page).isEqualTo(7)
        assertThat(list.first().name).isEqualTo("renamed.pdf")
        assertThat(list.first().lastOpenedMillis).isEqualTo(50)
    }

    @Test fun listIsCappedAt50() {
        val full = List(RecentsList.MAX_ENTRIES) { entry(it) }
        val list = RecentsList.touch(full, RecentVisit("content://a/new", "new.pdf"), nowMillis = 1)
        assertThat(list).hasSize(50)
        assertThat(list.first().uri).isEqualTo("content://a/new")
        assertThat(list.map { it.uri }).doesNotContain(full.last().uri)
    }

    @Test fun position_isStoredWithoutReordering() {
        val list = RecentsList.savePosition(listOf(entry(1), entry(2)), "content://a/2", page = 9, scale = "2", chapter = "Two")
        assertThat(list.map { it.uri }).containsExactly("content://a/1", "content://a/2").inOrder()
        assertThat(list.last().page).isEqualTo(9)
        assertThat(list.last().scale).isEqualTo("2")
        assertThat(list.last().chapter).isEqualTo("Two")
    }

    @Test fun positionOfAnUnknownDocument_changesNothing() {
        val list = listOf(entry(1))
        assertThat(RecentsList.savePosition(list, "content://a/9", page = 9, scale = "2", chapter = "")).isEqualTo(list)
    }

    @Test fun remove_leavesTheOthers() {
        assertThat(RecentsList.remove(listOf(entry(1), entry(2)), "content://a/1").map { it.uri })
            .containsExactly("content://a/2")
    }

    @Test fun touching_recordsTheLengthAndThePassword() {
        val list = RecentsList.touch(emptyList(), RecentVisit("content://a/2", "2.pdf", pages = 30, locked = true), nowMillis = 1)
        assertThat(list.single().pages).isEqualTo(30)
        assertThat(list.single().locked).isTrue()
    }

    @Test fun aFileWithAPassword_forgetsItsChapter() {
        val known = entry(1).copy(chapter = "Secret plans")
        val list = RecentsList.touch(listOf(known), RecentVisit(known.uri, known.name, pages = 3, locked = true), nowMillis = 1)
        assertThat(list.single().chapter).isEmpty()
    }

    @Test fun touching_keepsThePin() {
        val pinned = entry(1).copy(pinned = true)
        val list = RecentsList.touch(listOf(pinned), RecentVisit(pinned.uri, pinned.name), nowMillis = 1)
        assertThat(list.single().pinned).isTrue()
    }

    @Test fun theCap_neverDropsAPin() {
        val full = List(RecentsList.MAX_ENTRIES) { entry(it) }
        val pinnedLast = full.dropLast(1) + full.last().copy(pinned = true)
        val list = RecentsList.touch(pinnedLast, RecentVisit("content://a/new", "new.pdf"), nowMillis = 1)
        assertThat(list).hasSize(50)
        assertThat(list.map { it.uri }).contains(full.last().uri)
    }

    @Test fun pin_upToTheLimit() {
        val list = List(5) { entry(it) }
        val three = (0..2).fold(list) { acc, n -> checkNotNull(RecentsList.setPinned(acc, "content://a/$n", true)) }
        assertThat(three.count { it.pinned }).isEqualTo(RecentsList.MAX_PINNED)
        assertThat(RecentsList.setPinned(three, "content://a/4", true)).isNull()
    }

    @Test fun unpin_alwaysWorks() {
        val list = listOf(entry(1).copy(pinned = true))
        assertThat(RecentsList.setPinned(list, "content://a/1", false)?.single()?.pinned).isFalse()
    }

    @Test fun restore_putsTheEntryBackWhereItWas() {
        val list = RecentsList.restore(listOf(entry(3), entry(1)), entry(2))
        assertThat(list.map { it.uri }).containsExactly("content://a/3", "content://a/2", "content://a/1").inOrder()
    }

    @Test fun restore_doesNotDuplicate() {
        assertThat(RecentsList.restore(listOf(entry(2)), entry(2))).hasSize(1)
    }
}
