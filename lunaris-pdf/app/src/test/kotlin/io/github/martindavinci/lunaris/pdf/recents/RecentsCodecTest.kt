package io.github.martindavinci.lunaris.pdf.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsCodecTest {
    private val entry = RecentEntry(
        "content://a/1",
        "one.pdf",
        page = 4,
        scale = "1.5",
        lastOpenedMillis = 99L,
        pages = 30,
        chapter = "Two",
        locked = true,
        pinned = true,
    )

    @Test fun entries_surviveTheRoundTrip() {
        assertThat(RecentsCodec.fromJson(RecentsCodec.toJson(listOf(entry)))).containsExactly(entry)
    }

    @Test fun emptyOrBroken_givesNothing() {
        assertThat(RecentsCodec.fromJson("")).isEmpty()
        assertThat(RecentsCodec.fromJson("{not json")).isEmpty()
        assertThat(RecentsCodec.fromJson("""{"a":1}""")).isEmpty()
    }

    @Test fun corruptStoredRecents_giveWhatTheyCan() {
        val json = """[
            {"uri":"content://a/1","name":"one.pdf","page":2,"scale":"1","lastOpenedMillis":5},
            "nonsense",
            {"name":"no uri"},
            {"uri":"content://a/2"}
        ]"""
        val entries = RecentsCodec.fromJson(json)
        assertThat(entries.map { it.uri }).containsExactly("content://a/1", "content://a/2").inOrder()
    }

    @Test fun missingOrBadFields_getDefaults() {
        val single = RecentsCodec.fromJson("""[{"uri":"content://a/2","page":-4,"scale":7}]""").single()
        assertThat(single.page).isEqualTo(1)
        assertThat(single.name).isEqualTo("")
        assertThat(single.scale).isEqualTo("page-width")
        assertThat(single.pages).isEqualTo(0)
        assertThat(single.chapter).isEmpty()
        assertThat(single.locked).isFalse()
        assertThat(single.pinned).isFalse()
    }

    @Test fun repeatedUri_keepsTheFirst() {
        val json = """[{"uri":"content://a/1","name":"new"},{"uri":"content://a/1","name":"old"}]"""
        assertThat(RecentsCodec.fromJson(json).single().name).isEqualTo("new")
    }
}
