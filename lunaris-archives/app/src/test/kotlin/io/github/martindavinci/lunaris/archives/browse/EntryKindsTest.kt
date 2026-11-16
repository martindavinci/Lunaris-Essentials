package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.EntryType
import org.junit.Test

class EntryKindsTest {
    @Test fun byExtension() {
        assertThat(EntryKinds.of("notes.MD", EntryType.FILE)).isEqualTo(EntryKind.TEXT)
        assertThat(EntryKinds.of("budget.csv", EntryType.FILE)).isEqualTo(EntryKind.TABLE)
        assertThat(EntryKinds.of("plan.pdf", EntryType.FILE)).isEqualTo(EntryKind.PDF)
        assertThat(EntryKinds.of("cover.png", EntryType.FILE)).isEqualTo(EntryKind.IMAGE)
        assertThat(EntryKinds.of("clip.mp4", EntryType.FILE)).isEqualTo(EntryKind.VIDEO)
        assertThat(EntryKinds.of("song.flac", EntryType.FILE)).isEqualTo(EntryKind.AUDIO)
        assertThat(EntryKinds.of("Old version.zip", EntryType.FILE)).isEqualTo(EntryKind.ARCHIVE)
        assertThat(EntryKinds.of("backup.tar.gz", EntryType.FILE)).isEqualTo(EntryKind.ARCHIVE)
        assertThat(EntryKinds.of("data.bin", EntryType.FILE)).isEqualTo(EntryKind.OTHER)
        assertThat(EntryKinds.of("docs", EntryType.DIRECTORY)).isEqualTo(EntryKind.FOLDER)
    }
}
