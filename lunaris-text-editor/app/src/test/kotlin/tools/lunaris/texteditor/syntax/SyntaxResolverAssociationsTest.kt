package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyntaxResolverAssociationsTest {
    private val catalog = LanguageCatalog(listOf(
        LanguageDefinition("plaintext", "Plain text", null, null, listOf("txt")),
        LanguageDefinition("kotlin", "Kotlin", "source.kotlin", "grammars/kotlin.json", listOf("kt")),
        LanguageDefinition("java", "Java", "source.java", "grammars/java.json", listOf("java")),
    ))
    private val resolver = SyntaxResolver(catalog)

    @Test fun auto_associationOverridesBuiltinExtension() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "a.kt", associations = mapOf("kt" to "java"))).isEqualTo("source.java")
    }
    @Test fun auto_fallsBackToCatalogWhenNoAssociation() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "a.kt", associations = emptyMap())).isEqualTo("source.kotlin")
    }
    @Test fun auto_associationCaseInsensitiveExtension() {
        assertThat(resolver.resolveScope(SyntaxSelection.Auto, "a.KT", associations = mapOf("kt" to "java"))).isEqualTo("source.java")
    }
    @Test fun explicit_ignoresAssociations() {
        assertThat(resolver.resolveScope(SyntaxSelection.Explicit("kotlin"), "a.kt", mapOf("kt" to "java"))).isEqualTo("source.kotlin")
    }
    @Test fun none_ignoresAssociations() {
        assertThat(resolver.resolveScope(SyntaxSelection.None, "a.kt", mapOf("kt" to "java"))).isNull()
    }
}
