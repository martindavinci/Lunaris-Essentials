package tools.lunaris.texteditor.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyntaxModule {

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    @Provides
    @Singleton
    fun provideLanguageCatalog(@ApplicationContext context: Context): LanguageCatalog =
        LanguageCatalog.fromJson(readAsset(context, "textmate/languages.json"))

    @Provides
    @Singleton
    fun provideColorSchemeCatalog(@ApplicationContext context: Context): ColorSchemeCatalog =
        ColorSchemeCatalog.fromJson(readAsset(context, "textmate/themes.json"))

    @Provides
    @javax.inject.Named("defaultColorSchemeId")
    fun provideDefaultColorSchemeId(catalog: ColorSchemeCatalog): String = catalog.defaultId
}
