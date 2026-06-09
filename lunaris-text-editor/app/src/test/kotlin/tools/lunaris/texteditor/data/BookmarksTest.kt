package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookmarksTest {
    private fun b(uri: String, line: Int) = Bookmark(uri, uri.substringAfterLast('/'), line)

    @Test fun add_prependsAndDedupesByUriAndLine() {
        assertThat(Bookmarks.add(Bookmarks.add(emptyList(), b("u/1", 3)), b("u/1", 3))).hasSize(1)
    }
    @Test fun add_differentLineSameFile_keptSeparately() {
        assertThat(Bookmarks.add(Bookmarks.add(emptyList(), b("u/1", 3)), b("u/1", 9))).hasSize(2)
    }
    @Test fun remove_dropsMatch() {
        assertThat(Bookmarks.remove(listOf(b("u/1", 3), b("u/2", 1)), b("u/1", 3))).containsExactly(b("u/2", 1))
    }
    @Test fun jsonRoundTrip() {
        val start = listOf(b("u/1", 3), b("u/2", 10))
        assertThat(Bookmarks.fromJson(Bookmarks.toJson(start))).isEqualTo(start)
    }
}
