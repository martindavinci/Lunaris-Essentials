package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyntaxResolverTest {

    private val catalog = LanguageCatalog(
        listOf(
            LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
            LanguageDefinition("python", "Python", "source.python", "grammars/python.json", listOf("py")),
        )
    )
    private val resolver = SyntaxResolver(catalog)

    @Test
    fun auto_resolvesByExtension() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "main.py")).isEqualTo("source.python")
    }

    @Test
    fun auto_unknownExtension_isPlain() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "data.bin")).isNull()
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, null)).isNull()
    }

    @Test
    fun none_alwaysPlain() {
        assertThat(resolver.resolveScope(SyntaxSelection.None, "main.py")).isNull()
    }

    @Test
    fun explicit_usesSelectedLanguage() {
        assertThat(resolver.resolveScope(SyntaxSelection.Explicit("python"), "notes.txt"))
            .isEqualTo("source.python")
    }

    @Test
    fun explicit_unknownId_isPlain() {
        assertThat(resolver.resolveScope(SyntaxSelection.Explicit("ruby"), "a.py")).isNull()
    }

    @Test
    fun auto_fileWithoutExtension_isPlain() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "Makefile")).isNull()
    }
}
