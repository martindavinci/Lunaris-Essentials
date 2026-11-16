package io.github.martindavinci.lunaris.archives.safety

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RatioGuardTest {
    private val guard = RatioGuard(checkAfterBytes = 100, maxRatio = 10)

    @Test fun smallOutput_neverAsks() {
        guard.onEntryStart(); guard.onArchiveBytes(1_000)
        assertThat(guard.onOutput(99)).isEqualTo(RatioGuard.Verdict.CONTINUE)
    }

    @Test fun hugeExpansionPastTheThreshold_asks() {
        guard.onEntryStart(); guard.onArchiveBytes(1_000); guard.onArchiveBytes(1_005)
        assertThat(guard.onOutput(200)).isEqualTo(RatioGuard.Verdict.ASK)      // 200 / 5 = 40 > 10
    }

    @Test fun normalRatio_continues() {
        guard.onEntryStart(); guard.onArchiveBytes(1_000); guard.onArchiveBytes(1_050)
        assertThat(guard.onOutput(200)).isEqualTo(RatioGuard.Verdict.CONTINUE) // 200 / 50 = 4
    }

    /** One entry far into the archive, nothing extracted before it. */
    @Test fun anEntryDeepInTheArchive_isMeasuredFromItsOwnStart() {
        guard.onEntryStart(); guard.onArchiveBytes(50_000_000); guard.onArchiveBytes(50_000_004)
        assertThat(guard.onOutput(200)).isEqualTo(RatioGuard.Verdict.ASK)      // 4 bytes in, not 50 MB
    }

    @Test fun theStartIsNotTakenFromThePreviousEntry() {
        guard.onEntryStart(); guard.onArchiveBytes(900); guard.onArchiveBytes(1_000); guard.onOutput(150)
        guard.onEntryStart(); guard.onArchiveBytes(10); guard.onArchiveBytes(60)   // the reader went back in the file
        assertThat(guard.onOutput(200)).isEqualTo(RatioGuard.Verdict.CONTINUE)     // 200 / 50 = 4
    }

    @Test fun noReportAtAll_asks() {
        guard.onEntryStart()
        assertThat(guard.onOutput(200)).isEqualTo(RatioGuard.Verdict.ASK)
    }

    @Test fun allowedEntry_neverAsksAgain_untilTheNextEntry() {
        guard.onEntryStart(); guard.onArchiveBytes(0); guard.onArchiveBytes(1); guard.onOutput(200); guard.allowCurrentEntry()
        assertThat(guard.onOutput(10_000)).isEqualTo(RatioGuard.Verdict.CONTINUE)
        guard.onEntryStart(); guard.onArchiveBytes(1); guard.onArchiveBytes(2)
        assertThat(guard.onOutput(10_000)).isEqualTo(RatioGuard.Verdict.ASK)
    }
}
