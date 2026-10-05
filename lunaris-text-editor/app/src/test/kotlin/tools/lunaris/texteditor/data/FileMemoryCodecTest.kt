package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileMemoryCodecTest {

    @Test fun withSearch_putsNewestFirst_andDedupes() {
        var all = emptyMap<String, FileMemory>()
        all = FileMemoryCodec.withSearch(all, "u", "foo")
        all = FileMemoryCodec.withSearch(all, "u", "bar")
        all = FileMemoryCodec.withSearch(all, "u", "foo")
        assertThat(all["u"]!!.searches).containsExactly("foo", "bar").inOrder()
    }

    @Test fun withSearch_keepsOnlyTheLatestMax() {
        var all = emptyMap<String, FileMemory>()
        repeat(FileMemoryCodec.MAX_SEARCHES + 3) { all = FileMemoryCodec.withSearch(all, "u", "q$it") }
        assertThat(all["u"]!!.searches).hasSize(FileMemoryCodec.MAX_SEARCHES)
        assertThat(all["u"]!!.searches.first()).isEqualTo("q${FileMemoryCodec.MAX_SEARCHES + 2}")
    }

    @Test fun withSearch_ignoresBlankQueries() {
        assertThat(FileMemoryCodec.withSearch(emptyMap(), "u", "  ")).isEmpty()
    }

    @Test fun withCursor_keepsSearches_andUpdatesOffset() {
        var all = FileMemoryCodec.withSearch(emptyMap(), "u", "foo")
        all = FileMemoryCodec.withCursor(all, "u", 42)
        assertThat(all["u"]).isEqualTo(FileMemory(cursorOffset = 42, searches = listOf("foo")))
    }

    @Test fun capsRememberedFiles_droppingLeastRecentlyTouched() {
        var all = emptyMap<String, FileMemory>()
        repeat(FileMemoryCodec.MAX_FILES) { all = FileMemoryCodec.withCursor(all, "f$it", it) }
        all = FileMemoryCodec.withCursor(all, "f0", 1)          // f0 touched again: now newest
        all = FileMemoryCodec.withCursor(all, "new", 5)
        assertThat(all).hasSize(FileMemoryCodec.MAX_FILES)
        assertThat(all).containsKey("f0")
        assertThat(all).doesNotContainKey("f1")
    }

    @Test fun json_roundTrips() {
        val all = FileMemoryCodec.withSearch(FileMemoryCodec.withCursor(emptyMap(), "content://a", 9), "content://a", "x\"y")
        assertThat(FileMemoryCodec.fromJson(FileMemoryCodec.toJson(all))).isEqualTo(all)
    }

    @Test fun fromJson_blankOrCorrupt_isEmpty() {
        assertThat(FileMemoryCodec.fromJson("")).isEmpty()
        assertThat(FileMemoryCodec.fromJson("{not json")).isEmpty()
    }
}
