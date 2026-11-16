package io.github.martindavinci.lunaris.archives.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsHomeTest {
    private fun r(uri: String, t: Long) = RecentArchive(uri, uri, "ZIP", 1, 1, false, t, null)

    @Test fun upsertMovesToTopAndEvictsPastMax() {
        val list = listOf(r("u3", 3), r("u2", 2), r("u1", 1))
        val (kept, evicted) = RecentsHome.upsert(list, r("u2", 10), max = 2)
        assertThat(kept.map { it.uri }).containsExactly("u2", "u3").inOrder()
        assertThat(kept.first().lastOpenedMillis).isEqualTo(10)
        assertThat(evicted.map { it.uri }).containsExactly("u1")
    }
    @Test fun aNewItem_goesFirst() {
        val (kept, evicted) = RecentsHome.upsert(listOf(r("u1", 1)), r("u9", 9))
        assertThat(kept.map { it.uri }).containsExactly("u9", "u1").inOrder()
        assertThat(evicted).isEmpty()
    }
}
