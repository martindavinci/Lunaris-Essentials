package tools.lunaris.texteditor.syntax

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

object TextMateBootstrap {

    @Volatile private var done = false
    private var schemeCatalog: ColorSchemeCatalog? = null
    private val loadedThemes = mutableSetOf<String>()

    fun ensureRegistered(context: Context, languages: LanguageCatalog, schemes: ColorSchemeCatalog) {
        if (done) return
        synchronized(this) {
            if (done) return
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
            registerGrammars(languages)
            // Themes are loaded lazily (see ensureTheme): parsing all ~25 .tmTheme assets up front
            // slowed cold start; only the theme actually shown needs to be registered.
            schemeCatalog = schemes
            done = true
        }
    }

    /** Registers a single theme by id on first use, parsing its asset only then. */
    fun ensureTheme(id: String) {
        if (id in loadedThemes) return
        synchronized(this) {
            if (id in loadedThemes) return
            val scheme = schemeCatalog?.schemes?.firstOrNull { it.id == id } ?: return
            loadTheme(scheme)
            loadedThemes.add(id)
        }
    }

    private fun registerGrammars(catalog: LanguageCatalog) {
        GrammarRegistry.getInstance().loadGrammars(
            languages {
                catalog.languages.forEach { def ->
                    val scope = def.scopeName ?: return@forEach
                    val asset = def.grammarAsset ?: return@forEach
                    language(scope) {
                        grammar = "textmate/$asset"
                        // Register the grammar's real scope verbatim. Do NOT use
                        // defaultScopeName() — it prepends "source." and would double
                        // an already-qualified scope (source.source.asm.x86_64).
                        scopeName = scope
                    }
                }
            }
        )
    }

    private fun loadTheme(scheme: ColorSchemeDefinition) {
        val path = "textmate/${scheme.themeAsset}"
        val source = IThemeSource.fromInputStream(
            FileProviderRegistry.getInstance().tryGetInputStream(path), path, null,
        )
        try {
            ThemeRegistry.getInstance().loadTheme(ThemeModel(source, scheme.id))
        } catch (e: Exception) {
            android.util.Log.e("TextMateBootstrap", "Failed to load theme ${scheme.id}", e)
        }
    }
}
