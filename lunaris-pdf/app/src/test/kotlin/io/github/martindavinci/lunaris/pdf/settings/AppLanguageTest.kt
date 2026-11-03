package io.github.martindavinci.lunaris.pdf.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class AppLanguageTest {
    @Test fun normalize_blankOrMissing_isSystem() {
        assertThat(AppLanguage.normalize(null)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.normalize("")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test fun normalize_supportedTag_isKept() {
        AppLanguage.supported.forEach { assertThat(AppLanguage.normalize(it)).isEqualTo(it) }
    }

    @Test fun normalize_regionalVariant_mapsToShippedLanguage() {
        assertThat(AppLanguage.normalize("it-IT")).isEqualTo("it")
        assertThat(AppLanguage.normalize("pt-BR")).isEqualTo("pt")
        assertThat(AppLanguage.normalize("zh-Hans-CN")).isEqualTo("zh-CN")
    }

    @Test fun normalize_usesFirstOfList() {
        assertThat(AppLanguage.normalize("ja-JP,en-US")).isEqualTo("ja")
    }

    @Test fun normalize_unshippedLanguage_isSystem() {
        assertThat(AppLanguage.normalize("sv-SE")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test fun everySupportedLanguage_hasTranslations() {
        AppLanguage.supported.filter { it != AppLanguage.DEFAULT }.forEach { tag ->
            val qualifier = tag.replace("-", "-r")
            assertThat(File(RES, "values-$qualifier/strings.xml").isFile).isTrue()
        }
    }

    @Test fun localeConfig_listsExactlyTheSupportedLanguages() {
        val declared = Regex("""android:name="([^"]+)"""")
            .findAll(File(RES, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toList()
        assertThat(declared).containsExactlyElementsIn(AppLanguage.supported)
    }

    @Test fun everyTranslation_isShipped() {
        val shipped = File(RES).listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").isFile }!!
            .map { it.name.removePrefix("values-").replace("-r", "-") }
        assertThat(AppLanguage.supported).containsAtLeastElementsIn(shipped)
    }

    private companion object {
        // Unit tests run with the module directory as working directory.
        const val RES = "src/main/res"
    }
}
