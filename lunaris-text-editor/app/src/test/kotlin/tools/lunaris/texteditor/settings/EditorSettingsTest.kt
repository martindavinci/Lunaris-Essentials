package tools.lunaris.texteditor.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorSettingsTest {
    @Test fun defaults_matchReference() {
        val s = EditorSettings()
        assertThat(s.appTheme).isEqualTo(AppTheme.DARK)
        assertThat(s.lineNumbers).isTrue()
        assertThat(s.wordWrap).isTrue()
        assertThat(s.autoIndent).isTrue()
        assertThat(s.fontSizeSp).isEqualTo(16)
        assertThat(s.lineSpacingExtra).isEqualTo(2)
        assertThat(s.suggestions).isEqualTo(Suggestions.OFF)
        assertThat(s.sessionRestore).isFalse()
        assertThat(s.autosave).isFalse()
        assertThat(s.autosaveIntervalMinutes).isEqualTo(1)
        assertThat(s.defaultEncoding).isEqualTo("Auto")
        assertThat(s.defaultLineEnding).isEqualTo("Auto")
        assertThat(s.indentUnit).isEqualTo("\t")
        assertThat(s.folderDrawerEnabled).isTrue()
    }
    @Test fun jsonRoundTrip_allFields() {
        val s = EditorSettings(
            appTheme = AppTheme.BLACK, fullscreen = true, lineNumbers = false, wordWrap = false,
            autoIndent = false, fontSizeSp = 20, lineSpacingExtra = 4, monospace = false,
            sessionRestore = true, autosave = true, autosaveIntervalMinutes = 5,
            suggestions = Suggestions.OFF_AGGRESSIVE, autoCaps = true, accessoryBarDefault = true,
            undoWithBack = true, defaultEncoding = "UTF-8", defaultLineEnding = "CRLF",
            indentUnit = "    ", folderDrawerEnabled = false,
        )
        assertThat(EditorSettings.fromJson(s.toJson())).isEqualTo(s)
    }
    @Test fun fromJson_blank_isDefaults() {
        assertThat(EditorSettings.fromJson("")).isEqualTo(EditorSettings())
    }
    @Test fun fromJson_unknownEnum_fallsBackToDefault() {
        val s = EditorSettings.fromJson("""{"appTheme":"NEON","suggestions":"WAT"}""")
        assertThat(s.appTheme).isEqualTo(AppTheme.DARK)
        assertThat(s.suggestions).isEqualTo(Suggestions.OFF)
    }
}
