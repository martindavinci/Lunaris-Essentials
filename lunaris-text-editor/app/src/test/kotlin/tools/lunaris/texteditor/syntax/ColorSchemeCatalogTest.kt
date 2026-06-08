package tools.lunaris.texteditor.syntax

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorSchemeCatalogTest {

    private val json = this::class.java.classLoader!!
        .getResource("textmate/themes.json")!!.readText()
    private val catalog = ColorSchemeCatalog.fromJson(json)

    @Test
    fun parsesThemesAndDefault() {
        assertThat(catalog.defaultId).isEqualTo("sunburst")
        assertThat(catalog.schemes.map { it.display })
            .containsExactly("Sunburst", "Tomorrow Night")
    }

    @Test
    fun findById_works() {
        assertThat(catalog.findById("base16-tomorrow-night")?.themeAsset)
            .isEqualTo("themes/base16-tomorrow-night.tmTheme")
    }

    @Test
    fun defaultScheme_resolves() {
        assertThat(catalog.defaultScheme().display).isEqualTo("Sunburst")
    }

    @Test
    fun unknownDefault_fallsBackToFirstScheme() {
        val json = """{ "default": "nope", "themes": [ { "id":"a","display":"A","themeAsset":"themes/a.tmTheme","dark":true } ] }"""
        val c = ColorSchemeCatalog.fromJson(json)
        assertThat(c.defaultId).isEqualTo("a")
        assertThat(c.defaultScheme().id).isEqualTo("a")
    }
}
