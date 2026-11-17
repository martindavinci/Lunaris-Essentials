package io.github.martindavinci.lunaris.archives.files

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderModesTest {
    @Test fun readAndWholeWrites_areAllowed() {
        listOf("r", "w", "wt", "rw", "rwt").forEach { assertThat(ProviderModes.allows(it)).isTrue() }
    }
    @Test fun appendAndOthers_areRefused() {
        listOf("wa", "rwa", "", "x", "rwx").forEach { assertThat(ProviderModes.allows(it)).isFalse() }
    }
}
