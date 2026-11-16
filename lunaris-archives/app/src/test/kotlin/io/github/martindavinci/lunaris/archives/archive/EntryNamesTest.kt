package io.github.martindavinci.lunaris.archives.archive

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.SafePath
import org.junit.Test

class EntryNamesTest {
    private val override = Char(0x202E)

    @Test fun validUtf8_isUtf8EvenWithoutTheFlag() {
        assertThat(EntryNames.decode("café.md".toByteArray(), utf8Flag = false, zipWithoutEfs = true)).isEqualTo("café.md")
    }
    @Test fun invalidUtf8InAZipWithoutEfs_isCp437() {
        assertThat(EntryNames.decode(byteArrayOf(0x63, 0x61, 0x66, 0x82.toByte()), false, true)).isEqualTo("café")
        assertThat(EntryNames.decode(byteArrayOf(0xE1.toByte()), false, true)).isEqualTo("ß")
    }
    @Test fun invalidUtf8Elsewhere_getsReplacementCharacters() {
        assertThat(EntryNames.decode(byteArrayOf(0x61, 0x82.toByte()), false, false)).isEqualTo("a�")
        assertThat(EntryNames.decode(byteArrayOf(0x61, 0x82.toByte()), true, true)).isEqualTo("a�")
    }
    @Test fun cp437TableIsComplete() { assertThat(EntryNames.CP437_HIGH.length).isEqualTo(128) }

    @Test fun decode_keepsBidiControlsSoThePathRulesSeeOneComponent() {
        val name = "invoice" + override + "fdp.exe"
        val decoded = EntryNames.decode(name.toByteArray(), utf8Flag = true, zipWithoutEfs = false)
        assertThat(decoded).isEqualTo(name)
        assertThat(SafePath.check(decoded)).isEqualTo(SafePath.Result.Ok(listOf(name)))
    }
    @Test fun display_spellsBidiControlsOut() {
        val shown = EntryNames.display("a" + override + "b")
        assertThat(shown).doesNotContain(override.toString())
        assertThat(shown).contains("202E")
    }
}
