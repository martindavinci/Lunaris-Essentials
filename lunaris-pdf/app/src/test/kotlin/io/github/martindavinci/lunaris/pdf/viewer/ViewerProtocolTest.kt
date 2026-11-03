package io.github.martindavinci.lunaris.pdf.viewer

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ViewerProtocolTest {
    private fun payloadOf(call: String) = JSONObject(call.removePrefix("window.lunaris.handle(").removeSuffix(")"))

    @Test fun queryWithQuotesAndNewlines_cannotBreakOutOfTheCall() {
        assertThat(ViewerProtocol.encode(ViewerCommand.Find("a\"');alert(1)//\n", false))).doesNotContain("');alert(1)")
    }

    @Test fun query_survivesTheEncoding() {
        val query = "a\"');alert(1)//\n</script> è"
        assertThat(payloadOf(ViewerProtocol.encode(ViewerCommand.Find(query, true))).getString("query")).isEqualTo(query)
    }

    @Test fun password_survivesTheEncoding() {
        val call = ViewerProtocol.encode(ViewerCommand.Open(password = "p'w\"d", page = 3, scale = "1.5"))
        val payload = payloadOf(call)
        assertThat(payload.getString("type")).isEqualTo("open")
        assertThat(payload.getString("password")).isEqualTo("p'w\"d")
        assertThat(payload.getInt("page")).isEqualTo(3)
        assertThat(payload.getString("scale")).isEqualTo("1.5")
    }

    @Test fun noPassword_isLeftOut() {
        assertThat(payloadOf(ViewerProtocol.encode(ViewerCommand.Open(null, 1, "page-width"))).has("password")).isFalse()
    }

    @Test fun everyCommand_namesItsType() {
        val types = listOf(
            ViewerCommand.GoToPage(4) to "goToPage",
            ViewerCommand.CloseFind to "closeFind",
            ViewerCommand.SetNight(true) to "setNight",
        ).map { (command, type) -> payloadOf(ViewerProtocol.encode(command)).getString("type") to type }
        types.forEach { (actual, expected) -> assertThat(actual).isEqualTo(expected) }
    }

    @Test fun unknownEvent_isIgnored() {
        assertThat(ViewerProtocol.decode("""{"type":"x"}""")).isNull()
    }

    @Test fun malformedJson_isIgnored() {
        assertThat(ViewerProtocol.decode("{")).isNull()
        assertThat(ViewerProtocol.decode("[]")).isNull()
        assertThat(ViewerProtocol.decode("")).isNull()
    }

    @Test fun eventWithoutItsFields_isIgnored() {
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged"}""")).isNull()
        assertThat(ViewerProtocol.decode("""{"type":"linkTapped"}""")).isNull()
    }

    @Test fun pageZeroOrNegative_isIgnored() {
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":0,"scale":"1"}""")).isNull()
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":-3,"scale":"1"}""")).isNull()
        assertThat(ViewerProtocol.decode("""{"type":"loaded","pages":0,"outline":[]}""")).isNull()
    }

    @Test fun scaleThatIsNotOne_isIgnored() {
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":1,"scale":"<b>"}""")).isNull()
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":1,"scale":"1000"}""")).isNull()
    }

    @Test fun pageChanged_keepsItsScale() {
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":7,"scale":"page-width"}"""))
            .isEqualTo(ViewerEvent.PageChanged(7, "page-width"))
        assertThat(ViewerProtocol.decode("""{"type":"pageChanged","page":7,"scale":"1.25"}"""))
            .isEqualTo(ViewerEvent.PageChanged(7, "1.25"))
    }

    @Test fun loaded_decodesTheOutlineWithItsDepth() {
        val json = """{"type":"loaded","pages":12,"outline":[
            {"title":"One","page":1,"depth":0},
            {"title":"One point one","page":2,"depth":1},
            {"title":"Elsewhere","depth":1}]}"""
        assertThat(ViewerProtocol.decode(json)).isEqualTo(
            ViewerEvent.Loaded(
                12,
                listOf(OutlineItem("One", 1, 0), OutlineItem("One point one", 2, 1), OutlineItem("Elsewhere", null, 1)),
            ),
        )
    }

    @Test fun simpleEvents_decode() {
        assertThat(ViewerProtocol.decode("""{"type":"ready"}""")).isEqualTo(ViewerEvent.Ready)
        assertThat(ViewerProtocol.decode("""{"type":"tapped"}""")).isEqualTo(ViewerEvent.Tapped)
        assertThat(ViewerProtocol.decode("""{"type":"loadFailed"}""")).isEqualTo(ViewerEvent.LoadFailed)
        assertThat(ViewerProtocol.decode("""{"type":"passwordNeeded","wrong":true}"""))
            .isEqualTo(ViewerEvent.PasswordNeeded(true))
        assertThat(ViewerProtocol.decode("""{"type":"findResult","current":2,"total":9,"done":true}"""))
            .isEqualTo(ViewerEvent.FindResult(2, 9, true))
        assertThat(ViewerProtocol.decode("""{"type":"linkTapped","url":"https://example.com"}"""))
            .isEqualTo(ViewerEvent.LinkTapped("https://example.com"))
    }

    @Test fun storedScale_isCheckedBeforeUse() {
        assertThat(ViewerProtocol.isScale("page-width")).isTrue()
        assertThat(ViewerProtocol.isScale("0.5")).isTrue()
        assertThat(ViewerProtocol.isScale("nonsense")).isFalse()
    }

    @Test fun find_carriesItsOptions() {
        val payload = payloadOf(ViewerProtocol.encode(ViewerCommand.Find("a", false, caseSensitive = true, wholeWords = true)))
        assertThat(payload.getBoolean("caseSensitive")).isTrue()
        assertThat(payload.getBoolean("wholeWords")).isTrue()
    }

    @Test fun findResult_saysWhereTheMatchIs() {
        assertThat(ViewerProtocol.decode("""{"type":"findResult","current":2,"total":9,"done":true,"page":14,"onPage":3}"""))
            .isEqualTo(ViewerEvent.FindResult(2, 9, true, page = 14, onPage = 3))
    }

    @Test fun newCommands_nameTheirType() {
        mapOf(
            ViewerCommand.SetBarSpace(80, 90) to "setBarSpace",
            ViewerCommand.MakeThumbnail to "makeThumbnail",
        ).forEach { (command, type) ->
            assertThat(payloadOf(ViewerProtocol.encode(command)).getString("type")).isEqualTo(type)
        }
    }

    @Test fun scrollAndJumpEvents_decode() {
        assertThat(ViewerProtocol.decode("""{"type":"scrollChanged","moving":true,"down":false}"""))
            .isEqualTo(ViewerEvent.ScrollChanged(moving = true, down = false))
        assertThat(ViewerProtocol.decode("""{"type":"jumped","from":3,"to":20}""")).isEqualTo(ViewerEvent.Jumped(3, 20))
        assertThat(ViewerProtocol.decode("""{"type":"jumped","from":0,"to":20}""")).isNull()
        assertThat(ViewerProtocol.decode("""{"type":"chapterCrossed"}""")).isEqualTo(ViewerEvent.ChapterCrossed)
    }

    @Test fun thumbnail_decodesWithinItsLimit() {
        assertThat(ViewerProtocol.decode("""{"type":"thumbnail","jpeg":"AAAA"}""")).isEqualTo(ViewerEvent.Thumbnail("AAAA"))
        val huge = "A".repeat(ViewerProtocol.MAX_THUMBNAIL_CHARS + 1)
        assertThat(ViewerProtocol.decode("""{"type":"thumbnail","jpeg":"$huge"}""")).isNull()
    }
}
