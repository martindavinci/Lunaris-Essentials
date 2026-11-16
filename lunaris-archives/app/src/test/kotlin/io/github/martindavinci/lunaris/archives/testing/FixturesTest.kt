package io.github.martindavinci.lunaris.archives.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FixturesTest {
    @Test fun everyFixtureIsThere() {
        listOf(
            "plain.zip", "zip64.zip", "hostile.zip", "cp437.zip", "nul-name.zip", "reordered.zip",
            "hostile.tar", "plain.tar.gz", "not-archive.txt",
        ).forEach { assertThat(Fixtures.bytes(it)).isNotEmpty() }
    }
}
