package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveFormatTest {
    @Test fun choiceAndPacking_mapToFormats() {
        Packing.entries.forEach {
            assertThat(archiveFormat(FormatChoice.ZIP, it)).isEqualTo(ArchiveFormat.ZIP)
            assertThat(archiveFormat(FormatChoice.SEVEN_Z, it)).isEqualTo(ArchiveFormat.SEVEN_Z)
        }
        assertThat(Packing.entries.map { archiveFormat(FormatChoice.TAR, it) })
            .containsExactly(ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2).inOrder()
    }
    @Test fun onlyZip_takesAPassword() {
        assertThat(ArchiveFormat.entries.filter { it.takesPassword }).containsExactly(ArchiveFormat.ZIP)
    }
}
