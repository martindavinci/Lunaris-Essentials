package io.github.martindavinci.lunaris.archives.safety

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeComponentTest {
    @Test fun forbiddenCharacters_becomeUnderscores() {
        assertThat(SafeComponent.forWriting("a:b*c?d\"e<f>g|h")).isEqualTo("a_b_c_d_e_f_g_h")
        assertThat(SafeComponent.forWriting("tab\there\u007F")).isEqualTo("tab_here_")
    }
    @Test fun trailingDotsAndSpaces_areCut() { assertThat(SafeComponent.forWriting("name. . ")).isEqualTo("name") }
    @Test fun nothingLeft_isSkipped() {
        assertThat(SafeComponent.forWriting("...")).isNull()
        assertThat(SafeComponent.forWriting("  ")).isNull()
    }
    @Test fun nfdName_becomesNfc() {
        assertThat(SafeComponent.forWriting("Café.md")).isEqualTo("Café.md")
    }
    @Test fun longName_isCutTo255BytesKeepingTheExtension() {
        val out = SafeComponent.forWriting("è".repeat(300) + ".md")!!
        assertThat(out.toByteArray(Charsets.UTF_8).size).isAtMost(Limits.MAX_COMPONENT_BYTES)
        assertThat(out).endsWith(".md")
    }
    @Test fun longNameWithoutExtension_doesNotEndInADotAfterTheCut() {
        val cut = SafeComponent.forWriting("a".repeat(254) + ".b" + "c".repeat(40))!!
        assertThat(cut.toByteArray(Charsets.UTF_8).size).isAtMost(Limits.MAX_COMPONENT_BYTES)
        assertThat(cut).doesNotMatch(".*[. ]$")
    }
    @Test fun textReorderingCharacters_becomeUnderscores() {
        assertThat(SafeComponent.forWriting("invoice‮fdp.exe")).isEqualTo("invoice_fdp.exe")
        assertThat(SafeComponent.forWriting("a⁦b⁩c")).isEqualTo("a_b_c")
    }
    @Test fun leaf_isTheLastComponentMadeSafe() {
        assertThat(SafeComponent.leaf(SafePath.Result.Ok(listOf("docs", "a:b.txt")))).isEqualTo("a_b.txt")
    }
}
