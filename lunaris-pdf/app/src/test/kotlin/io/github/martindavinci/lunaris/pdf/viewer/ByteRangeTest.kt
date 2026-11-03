package io.github.martindavinci.lunaris.pdf.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ByteRangeTest {
    @Test fun range_isParsedAndClampedToTheFile() {
        assertThat(ByteRange.parse("bytes=100-199", 150)).isEqualTo(ByteRange(100, 149))
    }

    @Test fun exactRange_isKept() {
        assertThat(ByteRange.parse("bytes=0-65535", 1_000_000)).isEqualTo(ByteRange(0, 65_535))
    }

    @Test fun openEndedRange_goesToTheEnd() {
        assertThat(ByteRange.parse("bytes=100-", 150)).isEqualTo(ByteRange(100, 149))
    }

    @Test fun suffixRange_isTheLastBytes() {
        assertThat(ByteRange.parse("bytes=-50", 150)).isEqualTo(ByteRange(100, 149))
    }

    @Test fun suffixLongerThanTheFile_isTheWholeFile() {
        assertThat(ByteRange.parse("bytes=-500", 150)).isEqualTo(ByteRange(0, 149))
    }

    @Test fun rangePastTheEnd_isRejected() {
        assertThat(ByteRange.parse("bytes=200-", 150)).isNull()
    }

    @Test fun nonsense_isRejected() {
        listOf(null, "", "bytes=", "bytes=-", "bytes=a-b", "bytes=5-2", "items=0-1", "bytes=0-1,4-5", "bytes=-0")
            .forEach { assertThat(ByteRange.parse(it, 150)).isNull() }
    }

    @Test fun emptyFile_hasNoRange() {
        assertThat(ByteRange.parse("bytes=0-", 0)).isNull()
    }

    @Test fun length_countsBothEnds() {
        assertThat(ByteRange(100, 149).length).isEqualTo(50)
    }
}
