package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationLabelTest {
    @Test fun primaryStorage() {
        assertThat(LocationLabel.of("primary:Documents/Project")).isEqualTo("Documents / Project")
        assertThat(LocationLabel.of("primary:Download")).isEqualTo("Download")
        assertThat(LocationLabel.of("primary:")).isNull()
        assertThat(LocationLabel.of("primary:a//b/")).isEqualTo("a / b")
    }
    @Test fun otherVolumesAndProviders() {
        assertThat(LocationLabel.of("1A2B-3C4D:Music/Live")).isEqualTo("Music / Live")
        assertThat(LocationLabel.of("msf:1234")).isNull()
        assertThat(LocationLabel.of("no colon")).isNull()
        assertThat(LocationLabel.of(null)).isNull()
    }
}
