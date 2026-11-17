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
    @Test fun fileName_addsTheSuffixOfEachFormat() {
        val names = ArchiveFormat.entries.map { ArchiveNameRules.fileName("Trip", it) }
        assertThat(names).containsExactly("Trip.zip", "Trip.7z", "Trip.tar", "Trip.tar.gz", "Trip.tar.xz", "Trip.tar.bz2").inOrder()
    }
    @Test fun fileName_keepsASuffixAlreadyTyped_inAnyCase() {
        assertThat(ArchiveNameRules.fileName("Trip.TAR.GZ", ArchiveFormat.TAR_GZ)).isEqualTo("Trip.TAR.GZ")
        assertThat(ArchiveNameRules.fileName("Trip.7Z", ArchiveFormat.SEVEN_Z)).isEqualTo("Trip.7Z")
    }
    @Test fun fileName_completesABareTar() {
        assertThat(ArchiveNameRules.fileName("Trip.tar", ArchiveFormat.TAR_GZ)).isEqualTo("Trip.tar.gz")
        assertThat(ArchiveNameRules.fileName("Trip.TAR", ArchiveFormat.TAR_XZ)).isEqualTo("Trip.TAR.xz")
        assertThat(ArchiveNameRules.fileName("Trip.tar", ArchiveFormat.TAR)).isEqualTo("Trip.tar")
    }
    @Test fun isValid_countsTheLongerSuffix() {
        val base = "a".repeat(MAX_BASE_FOR_ZIP)
        assertThat(ArchiveNameRules.isValid(base, ArchiveFormat.ZIP)).isTrue()
        assertThat(ArchiveNameRules.isValid(base, ArchiveFormat.TAR_BZ2)).isFalse()
    }

    private companion object { const val MAX_BASE_FOR_ZIP = 255 - 4 }
}
