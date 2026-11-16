package io.github.martindavinci.lunaris.archives.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveKeysTest {
    @Test fun sixteenHexCharacters_stableAndSensitiveToEveryPart() {
        val k = ArchiveKeys.of("content://a/1", 10, 20)
        assertThat(k).matches("[0-9a-f]{16}")
        assertThat(ArchiveKeys.of("content://a/1", 10, 20)).isEqualTo(k)
        assertThat(ArchiveKeys.of("content://a/2", 10, 20)).isNotEqualTo(k)
        assertThat(ArchiveKeys.of("content://a/1", 11, 20)).isNotEqualTo(k)
        assertThat(ArchiveKeys.of("content://a/1", 10, null)).isNotEqualTo(k)
        assertThat(ArchiveKeys.of("content://a/1", null, 20)).isNotEqualTo(k)
    }
}
