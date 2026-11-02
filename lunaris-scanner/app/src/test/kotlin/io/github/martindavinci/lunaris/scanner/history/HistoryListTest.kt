package io.github.martindavinci.lunaris.scanner.history

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import org.junit.Test

class HistoryListTest {
    private val qr = ScannedCode("hello", CodeFormat.QR_CODE)

    @Test fun add_toEmptyList_createsTheFirstEntry() {
        assertThat(HistoryList.add(emptyList(), qr, nowMillis = 10))
            .containsExactly(HistoryEntry(1, "hello", CodeFormat.QR_CODE, 10))
    }

    @Test fun add_putsTheNewestFirst() {
        val first = HistoryList.add(emptyList(), qr, nowMillis = 10)

        val second = HistoryList.add(first, ScannedCode("world", CodeFormat.QR_CODE), nowMillis = 20)

        assertThat(second.map { it.text }).containsExactly("world", "hello").inOrder()
    }

    @Test fun add_consecutiveDuplicate_refreshesTheTimestampAndKeepsTheId() {
        val first = HistoryList.add(emptyList(), qr, nowMillis = 10)

        assertThat(HistoryList.add(first, qr, nowMillis = 20))
            .containsExactly(HistoryEntry(1, "hello", CodeFormat.QR_CODE, 20))
    }

    @Test fun add_sameTextInAnotherFormat_isNotADuplicate() {
        val first = HistoryList.add(emptyList(), qr, nowMillis = 10)

        assertThat(HistoryList.add(first, ScannedCode("hello", CodeFormat.AZTEC), nowMillis = 20)).hasSize(2)
    }

    @Test fun add_nonConsecutiveDuplicate_isANewEntry() {
        val entries = listOf("a", "b", "a").fold(emptyList<HistoryEntry>()) { list, text ->
            HistoryList.add(list, ScannedCode(text, CodeFormat.QR_CODE), nowMillis = 10)
        }

        assertThat(entries.map { it.text }).containsExactly("a", "b", "a").inOrder()
    }

    @Test fun add_beyondTheCap_dropsTheOldest() {
        val full = (1..HistoryList.MAX_ENTRIES).map { HistoryEntry(it.toLong(), "text $it", CodeFormat.QR_CODE, it.toLong()) }

        val result = HistoryList.add(full, qr, nowMillis = 1_000)

        assertThat(result).hasSize(HistoryList.MAX_ENTRIES)
        assertThat(result.first().text).isEqualTo("hello")
        assertThat(result.last()).isEqualTo(full[full.lastIndex - 1])
    }

    @Test fun add_alwaysPicksAnUnusedId() {
        val entries = listOf(HistoryEntry(7, "a", CodeFormat.QR_CODE, 1), HistoryEntry(3, "b", CodeFormat.QR_CODE, 1))

        assertThat(HistoryList.add(entries, qr, nowMillis = 2).first().id).isEqualTo(8)
    }

    @Test fun delete_removesOnlyThatEntry() {
        val keep = HistoryEntry(1, "a", CodeFormat.QR_CODE, 1)
        val gone = HistoryEntry(2, "b", CodeFormat.QR_CODE, 1)

        assertThat(HistoryList.delete(listOf(gone, keep), 2)).containsExactly(keep)
    }

    @Test fun delete_unknownId_changesNothing() {
        val entries = listOf(HistoryEntry(1, "a", CodeFormat.QR_CODE, 1))

        assertThat(HistoryList.delete(entries, 99)).isEqualTo(entries)
    }
}
