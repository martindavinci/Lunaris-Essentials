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

    fun ensureRegistered(context: Context, languages: LanguageCatalog, schemes: ColorSchemeCatalog) {
        if (done) return
        synchronized(this) {
            if (done) return
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
            registerGrammars(languages)
            registerThemes(schemes)
            done = true
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
                        defaultScopeName()
                    }
                }
            }
        )
    }

    private fun registerThemes(catalog: ColorSchemeCatalog) {
        val registry = ThemeRegistry.getInstance()
        catalog.schemes.forEach { scheme ->
            val path = "textmate/${scheme.themeAsset}"
            val source = IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(path), path, null,
            )
            try {
                registry.loadTheme(ThemeModel(source, scheme.id))
            } catch (e: Exception) {
                android.util.Log.e("TextMateBootstrap", "Failed to load theme ${scheme.id}", e)
            }
        }
    }
}
