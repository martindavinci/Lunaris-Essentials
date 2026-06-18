package tools.lunaris.texteditor.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tools.lunaris.texteditor.data.RecentEntry

class BackupCodecTest {
    @Test fun roundTrip() {
        val backup = Backup(
            settings = EditorSettings(fontSizeSp = 18, appTheme = AppTheme.BLACK),
            associations = mapOf("kt" to "kotlin"),
            colorSchemeId = "base16-ocean",
            recents = listOf(RecentEntry("content://1", "a.kt")),
        )
        assertThat(BackupCodec.fromJson(BackupCodec.toJson(backup))).isEqualTo(backup)
    }
    @Test fun fromJson_blank_isEmptyDefaults() {
        val b = BackupCodec.fromJson("")
        assertThat(b.settings).isEqualTo(EditorSettings())
        assertThat(b.associations).isEmpty()
        assertThat(b.recents).isEmpty()
        assertThat(b.colorSchemeId).isEmpty()
    }
}
