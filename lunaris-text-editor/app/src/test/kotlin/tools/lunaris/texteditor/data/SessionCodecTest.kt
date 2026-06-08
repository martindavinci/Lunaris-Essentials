package tools.lunaris.texteditor.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionCodecTest {

    @Test
    fun roundTrip_preservesTabsAndActive() {
        val session = PersistedSession(
            activeId = "t2",
            tabs = listOf(
                PersistedTab("t1", uri = "content://1", title = "a.kt", text = "fun x", dirty = false, charsetName = "UTF-8", lineEnding = "LF", syntaxId = null),
                PersistedTab("t2", uri = null, title = "untitled", text = "draft", dirty = true, charsetName = "UTF-8", lineEnding = "LF", syntaxId = "python"),
            ),
        )
        val restored = SessionCodec.fromJson(SessionCodec.toJson(session))
        assertThat(restored).isEqualTo(session)
    }

    @Test
    fun fromJson_blank_isEmptySession() {
        val s = SessionCodec.fromJson("")
        assertThat(s.tabs).isEmpty()
        assertThat(s.activeId).isEmpty()
    }
}
