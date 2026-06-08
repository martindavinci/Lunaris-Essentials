package tools.lunaris.texteditor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.TextMateBootstrap
import javax.inject.Inject

@HiltAndroidApp
class LunarisEditorApp : Application() {

    @Inject lateinit var languageCatalog: LanguageCatalog
    @Inject lateinit var colorSchemeCatalog: ColorSchemeCatalog

    override fun onCreate() {
        super.onCreate()
        TextMateBootstrap.ensureRegistered(this, languageCatalog, colorSchemeCatalog)
    }
}
