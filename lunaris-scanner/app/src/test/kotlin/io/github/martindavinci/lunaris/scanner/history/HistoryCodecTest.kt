package io.github.martindavinci.lunaris.scanner.history

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import org.junit.Test

class HistoryCodecTest {
    @Test fun roundTrip_keepsEveryFieldAndTheOrder() {
        val entries = listOf(
            HistoryEntry(2, "line one\nline \"two\" è", CodeFormat.EAN_13, 200),
            HistoryEntry(1, "WIFI:S:Home;;", CodeFormat.QR_CODE, 100),
        )

        assertThat(HistoryCodec.fromJson(HistoryCodec.toJson(entries))).isEqualTo(entries)
    }

    @Test fun emptyList_roundTrips() {
        assertThat(HistoryCodec.fromJson(HistoryCodec.toJson(emptyList()))).isEmpty()
    }

    @Test fun blankOrGarbage_isEmpty() {
        assertThat(HistoryCodec.fromJson("")).isEmpty()
        assertThat(HistoryCodec.fromJson("   ")).isEmpty()
        assertThat(HistoryCodec.fromJson("not json")).isEmpty()
        assertThat(HistoryCodec.fromJson("{\"id\":1}")).isEmpty()
        assertThat(HistoryCodec.fromJson("[{\"id\":1,")).isEmpty()
    }

    @Test fun badElements_areSkippedAndTheRestKept() {
        val json = """
            [
              "junk",
              42,
              {"id":1,"text":"good","format":"QR_CODE","timestampMillis":5},
              {"text":"no id","format":"QR_CODE","timestampMillis":5},
              {"id":2,"format":"QR_CODE","timestampMillis":5},
              {"id":3,"text":"no format","timestampMillis":5},
              {"id":4,"text":"unknown format","format":"NOPE","timestampMillis":5},
              {"id":5,"text":"also good","format":"AZTEC","timestampMillis":6}
            ]
        """.trimIndent()

        assertThat(HistoryCodec.fromJson(json)).containsExactly(
            HistoryEntry(1, "good", CodeFormat.QR_CODE, 5),
            HistoryEntry(5, "also good", CodeFormat.AZTEC, 6),
        ).inOrder()
    }

    @Test fun missingTimestamp_keepsTheEntryWithAnUnknownTime() {
        val json = """[{"id":1,"text":"a","format":"QR_CODE"}]"""

        assertThat(HistoryCodec.fromJson(json)).containsExactly(HistoryEntry(1, "a", CodeFormat.QR_CODE, 0))
    }

    @Test fun repeatedIds_keepTheFirstEntry() {
        val json = """
            [
              {"id":1,"text":"first","format":"QR_CODE","timestampMillis":2},
              {"id":1,"text":"second","format":"QR_CODE","timestampMillis":1}
            ]
        """.trimIndent()

        assertThat(HistoryCodec.fromJson(json)).containsExactly(HistoryEntry(1, "first", CodeFormat.QR_CODE, 2))
    }
}
