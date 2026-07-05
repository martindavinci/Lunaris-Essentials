package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class LanguageCatalogTest {

    private val json = this::class.java.classLoader!!
        .getResource("textmate/languages.json")!!.readText()
    private val catalog = LanguageCatalog.fromJson(json)

    @Test
    fun parsesAllLanguages() {
        assertThat(catalog.languages.map { it.id })
            .containsExactly("plaintext", "python", "kotlin")
    }

    @Test
    fun findByExtension_isCaseInsensitive() {
        assertThat(catalog.findByExtension("PY")?.id).isEqualTo("python")
        assertThat(catalog.findByExtension("kts")?.id).isEqualTo("kotlin")
    }

    @Test
    fun findByExtension_unknown_returnsNull() {
        assertThat(catalog.findByExtension("xyz")).isNull()
    }

    @Test
    fun findById_works() {
        assertThat(catalog.findById("kotlin")?.scopeName).isEqualTo("source.kotlin")
    }

    @Test
    fun plaintext_hasNullScope() {
        assertThat(catalog.findById("plaintext")?.scopeName).isNull()
    }

    @Test
    fun findById_unknown_returnsNull() {
        assertThat(catalog.findById("ruby")).isNull()
    }

    @Test
    fun findByExtension_emptyExtLanguage_neverMatches() {
        val c = LanguageCatalog(
            listOf(LanguageDefinition("none", "None", null, null, emptyList()))
        )
        assertThat(c.findByExtension("none")).isNull()
        assertThat(c.findById("none")).isNotNull()
    }

    @Test
    fun ext_acceptsSingleStringForm() {
        val c = LanguageCatalog.fromJson(
            """{"languages":[{"id":"csharp","display":"C#","scopeName":"source.cs",""" +
                """"grammarAsset":"grammars/csharp.json","ext":"cs"}]}"""
        )
        assertThat(c.findByExtension("cs")?.id).isEqualTo("csharp")
    }

    // Guards the real shipped asset (not the small test fixture): single-string "ext"
    // entries like csharp's "cs" must parse — this is the crash that slipped to device.
    @Test
    fun realShippedAsset_parsesWithoutThrowing_andResolvesStringExt() {
        val asset = File("src/main/assets/textmate/languages.json")
        assertThat(asset.exists()).isTrue()
        val c = LanguageCatalog.fromJson(asset.readText())
        assertThat(c.languages.size).isAtLeast(40)
        assertThat(c.findByExtension("cs")?.id).isEqualTo("csharp")
    }

    @Test
    fun findByScope_knownScope_returnsLanguage() {
        assertThat(catalog.findByScope("source.python")?.id).isEqualTo("python")
    }

    @Test
    fun findByScope_nullScope_returnsPlaintext() {
        assertThat(catalog.findByScope(null)?.id).isEqualTo("plaintext")
    }

    @Test
    fun findByScope_unknownScope_returnsNull() {
        assertThat(catalog.findByScope("source.nope")).isNull()
    }

    @Test
    fun displayName_explicit_returnsCatalogDisplay() {
        assertThat(catalog.displayName(SyntaxSelection.Explicit("kotlin"), scopeName = null))
            .isEqualTo("Kotlin")
    }

    @Test
    fun displayName_autoResolvedScope_returnsCatalogDisplay() {
        assertThat(catalog.displayName(SyntaxSelection.Auto, scopeName = "source.python"))
            .isEqualTo("Python")
    }

    @Test
    fun displayName_plaintext_returnsNull() {
        assertThat(catalog.displayName(SyntaxSelection.Auto, scopeName = null)).isNull()
        assertThat(catalog.displayName(SyntaxSelection.Explicit("plaintext"), scopeName = null)).isNull()
    }

    @Test
    fun findByExtension_duplicateExtension_lastWins() {
        val c = LanguageCatalog(
            listOf(
                LanguageDefinition("a", "A", "source.a", "grammars/a.json", listOf("x")),
                LanguageDefinition("b", "B", "source.b", "grammars/b.json", listOf("x")),
            )
        )
        assertThat(c.findByExtension("x")?.id).isEqualTo("b")
    }
}
