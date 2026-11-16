package io.github.martindavinci.lunaris.archives.extract

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import org.junit.Test

class KeepBothNamerTest {
    @Test fun firstFreeNumberBeforeTheExtension() {
        assertThat(KeepBothNamer.name("notes.md", setOf("notes.md"))).isEqualTo("notes (1).md")
        assertThat(KeepBothNamer.name("notes.md", setOf("notes.md", "notes (1).md"))).isEqualTo("notes (2).md")
    }
    @Test fun takenIsCaseInsensitive() { assertThat(KeepBothNamer.name("Notes.md", setOf("notes.md", "notes (1).md"))).isEqualTo("Notes (2).md") }
    @Test fun noExtensionAndDotfiles() {
        assertThat(KeepBothNamer.name("README", setOf("readme"))).isEqualTo("README (1)")
        assertThat(KeepBothNamer.name(".env", setOf(".env"))).isEqualTo(".env (1)")
        assertThat(KeepBothNamer.name("a.tar.gz", setOf("a.tar.gz"))).isEqualTo("a.tar (1).gz")
    }
    @Test fun aLongName_staysWithinTheLimitAndFree() {
        val long = "n".repeat(250) + ".md"
        val first = KeepBothNamer.name(long, setOf(long))
        assertThat(first.toByteArray().size).isAtMost(Limits.MAX_COMPONENT_BYTES)
        assertThat(first).endsWith(" (1).md")
        val second = KeepBothNamer.name(long, setOf(long, first))
        assertThat(second).isNotEqualTo(first)
        assertThat(second).endsWith(" (2).md")
    }
}
