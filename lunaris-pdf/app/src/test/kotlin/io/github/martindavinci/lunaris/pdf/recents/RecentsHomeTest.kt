package io.github.martindavinci.lunaris.pdf.recents

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Test

class RecentsHomeTest {
    private val zone = ZoneOffset.UTC
    // A Wednesday.
    private val now = millis(2026, 11, 4, 10)

    private fun millis(year: Int, month: Int, day: Int, hour: Int) =
        LocalDateTime.of(year, month, day, hour, 0).toInstant(zone).toEpochMilli()

    private fun entry(name: String, opened: Long, pinned: Boolean = false) =
        RecentEntry("content://a/$name", "$name.pdf", lastOpenedMillis = opened, pinned = pinned)

    private fun home(entries: List<RecentEntry>, query: String? = null) = RecentsHome.of(entries, query)

    @Test fun nothing_givesAnEmptyHome() {
        val home = home(emptyList())
        assertThat(home.resume).isNull()
        assertThat(home.pinned).isEmpty()
        assertThat(home.all).isEmpty()
    }

    @Test fun theNewest_isTheOneToResume_andStaysInTheList() {
        val home = home(listOf(entry("a", millis(2026, 11, 4, 9)), entry("b", millis(2026, 11, 4, 8))))
        assertThat(home.resume?.name).isEqualTo("a.pdf")
        assertThat(home.all.map { it.name }).containsExactly("a.pdf", "b.pdf").inOrder()
    }

    @Test fun aSingleFile_isBothResumedAndListed() {
        val home = home(listOf(entry("a", millis(2026, 11, 4, 9))))
        assertThat(home.resume?.name).isEqualTo("a.pdf")
        assertThat(home.all.map { it.name }).containsExactly("a.pdf")
    }

    @Test fun pinned_goesAbove_andIsNotResumedOrListedTwice() {
        val home = home(listOf(entry("a", millis(2026, 11, 4, 9), pinned = true), entry("b", millis(2026, 11, 4, 8))))
        assertThat(home.pinned.map { it.name }).containsExactly("a.pdf")
        assertThat(home.resume?.name).isEqualTo("b.pdf")
        assertThat(home.all.map { it.name }).containsExactly("b.pdf")
    }

    @Test fun theList_isNewestFirst_whateverItsAge() {
        val home = home(
            listOf(
                entry("today", millis(2026, 11, 4, 9)),
                entry("weekAgo", millis(2026, 10, 29, 9)),
                entry("old", millis(2026, 10, 1, 9)),
                entry("unknown", 0),
            ),
        )
        assertThat(home.all.map { it.name })
            .containsExactly("today.pdf", "weekAgo.pdf", "old.pdf", "unknown.pdf").inOrder()
    }

    @Test fun searching_listsEverythingThatMatches_withoutGroups() {
        val entries = listOf(entry("Moon", millis(2026, 11, 4, 9), pinned = true), entry("Sun", millis(2026, 11, 4, 8)))
        val home = home(entries, query = "moo")
        assertThat(home.results?.map { it.name }).containsExactly("Moon.pdf")
        assertThat(home.resume).isNull()
        assertThat(home.pinned).isEmpty()
        assertThat(home(entries, query = "").results).hasSize(2)
    }

    @Test fun whenLabel_isTheTimeToday_thenADay_thenADate() {
        val today = millis(2026, 11, 4, 8)
        assertThat(RecentsHome.whenLabel(today, now, zone)).isEqualTo(WhenLabel.At(today))
        assertThat(RecentsHome.whenLabel(millis(2026, 11, 3, 8), now, zone)).isEqualTo(WhenLabel.Yesterday)
        assertThat(RecentsHome.whenLabel(millis(2026, 11, 1, 8), now, zone)).isEqualTo(WhenLabel.Weekday(DayOfWeek.SUNDAY))
        assertThat(RecentsHome.whenLabel(millis(2026, 10, 28, 8), now, zone))
            .isEqualTo(WhenLabel.Day(LocalDate.of(2026, 10, 28)))
    }

    @Test fun whenLabel_ofAnUnknownTime_isNull() {
        assertThat(RecentsHome.whenLabel(0, now, zone)).isNull()
    }
}
