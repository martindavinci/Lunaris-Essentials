package io.github.martindavinci.lunaris.archives.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsCodecTest {
    private val item = RecentArchive("content://a/1", "Project notes.zip", "ZIP", 2_400_000, 12, locked = false, lastOpenedMillis = 5, lastPath = listOf("docs"))
    private val bare = item.copy(uri = "content://a/2", lastPath = null, sizeBytes = null, locked = true)

    @Test fun roundTrip() {
        assertThat(RecentsCodec.decode(RecentsCodec.encode(listOf(item, bare)))).containsExactly(item, bare).inOrder()
    }
    @Test fun garbage_isEmpty() {
        assertThat(RecentsCodec.decode("not json")).isEmpty()
        assertThat(RecentsCodec.decode("{}")).isEmpty()
    }
    @Test fun duplicatesAndBadRows_areDropped() {
        val rows = RecentsCodec.decode("""[{"uri":"content://a/1","name":"x","format":"ZIP","files":1,"opened":1},{"uri":"content://a/1","name":"y","format":"ZIP","files":1,"opened":2},{"name":"no uri"},42]""")
        assertThat(rows.map { it.name }).containsExactly("x")
    }
    @Test fun negatives_becomeZero() {
        val row = RecentsCodec.decode("""[{"uri":"content://a/1","name":"x","format":"ZIP","size":-5,"files":-1,"opened":-9}]""").single()
        assertThat(row.sizeBytes).isEqualTo(0); assertThat(row.fileCount).isEqualTo(0); assertThat(row.lastOpenedMillis).isEqualTo(0)
    }
}
