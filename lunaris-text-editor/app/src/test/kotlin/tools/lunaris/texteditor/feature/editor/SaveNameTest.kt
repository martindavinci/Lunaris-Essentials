package tools.lunaris.texteditor.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxSelection

class SaveNameTest {
    private val catalog = LanguageCatalog(
        listOf(
            LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py", "pyw")),
            LanguageDefinition("cpp", "C++", "source.cpp", "grammars/cpp.json", listOf("cpp", "cc")),
        )
    )

    @Test fun untitled_auto_defaultsToTxt() {
        assertThat(suggestedSaveName(EditorUiState(title = "untitled", syntax = SyntaxSelection.Auto), catalog))
            .isEqualTo("untitled.txt")
    }

    @Test fun untitled_explicit_usesPrimaryExtension() {
        assertThat(suggestedSaveName(EditorUiState(title = "untitled", syntax = SyntaxSelection.Explicit("python")), catalog))
            .isEqualTo("untitled.py")
        assertThat(suggestedSaveName(EditorUiState(title = "untitled", syntax = SyntaxSelection.Explicit("cpp")), catalog))
            .isEqualTo("untitled.cpp")
    }

    @Test fun untitled_unknownLanguage_defaultsToTxt() {
        assertThat(suggestedSaveName(EditorUiState(title = "untitled", syntax = SyntaxSelection.Explicit("zzz")), catalog))
            .isEqualTo("untitled.txt")
    }

    @Test fun namedFile_keepsItsName_trimmed() {
        assertThat(suggestedSaveName(EditorUiState(title = "notes.md "), catalog)).isEqualTo("notes.md")
    }
}
