package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
