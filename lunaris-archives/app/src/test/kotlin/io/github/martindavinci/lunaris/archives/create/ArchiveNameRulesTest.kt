package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveNameRulesTest {
    @Test fun forbiddenCharactersAndBlank_areInvalid() {
        listOf("", "  ", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "tab\t")
            .forEach { assertThat(ArchiveNameRules.isValid(it)).isFalse() }
        assertThat(ArchiveNameRules.isValid("Project notes")).isTrue()
    }
    @Test fun tooLong_isInvalid() { assertThat(ArchiveNameRules.isValid("é".repeat(130))).isFalse() }
    @Test fun suggestions() {
        assertThat(ArchiveNameRules.suggested(listOf("photo.jpg"), null)).isEqualTo("photo")
        assertThat(ArchiveNameRules.suggested(listOf("a.jpg", "b.jpg"), "Holiday")).isEqualTo("Holiday")
        assertThat(ArchiveNameRules.suggested(listOf("a.jpg", "b.jpg"), null)).isEqualTo(ArchiveNameRules.SHARED_FILES_FALLBACK)
        assertThat(ArchiveNameRules.suggested(listOf(".env"), null)).isEqualTo(".env")
    }
    @Test fun fileNameAddsZipOnce() {
        assertThat(ArchiveNameRules.fileName("Project notes")).isEqualTo("Project notes.zip")
        assertThat(ArchiveNameRules.fileName("x.ZIP")).isEqualTo("x.ZIP")
    }
}
