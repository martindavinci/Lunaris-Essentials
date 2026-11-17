package io.github.martindavinci.lunaris.archives.feature.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.extract.SkippedEntry
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import org.junit.Test

class SkipReasonsTest {
    private val reasons = SkipReasons(link = "link", device = "device file", invalid = "invalid name", pattern = "%1\$s (%2\$s)")

    @Test fun outside_isTheNameAlone_andTheOthersSayWhy() {
        assertThat(reasons.line(SkippedEntry("../x", SkipReason.OUTSIDE_FOLDER))).isEqualTo("../x")
        assertThat(reasons.line(SkippedEntry("a/l", SkipReason.LINK))).isEqualTo("a/l (link)")
        assertThat(reasons.line(SkippedEntry("dev/null", SkipReason.DEVICE_FILE))).isEqualTo("dev/null (device file)")
        assertThat(reasons.line(SkippedEntry("...", SkipReason.INVALID_NAME))).isEqualTo("... (invalid name)")
    }
    @Test fun aNameWithAPercentSign_isLeftAsItIs() {
        assertThat(reasons.line(SkippedEntry("100%s.txt", SkipReason.LINK))).isEqualTo("100%s.txt (link)")
    }
}
