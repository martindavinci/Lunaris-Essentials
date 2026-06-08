package tools.lunaris.texteditor.edit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimestampFormatterTest {
    @Test fun formatsLocalDateTime() {
        val s = TimestampFormatter.format(Instant.parse("2026-06-08T15:42:00Z"), ZoneOffset.UTC)
        assertThat(s).isEqualTo("2026-06-08 15:42:00")
    }
}
