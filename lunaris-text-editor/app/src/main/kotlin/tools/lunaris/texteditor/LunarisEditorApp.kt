package tools.lunaris.texteditor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.RecentsStore
import tools.lunaris.texteditor.feature.editor.prepareShareDir
import tools.lunaris.texteditor.files.StorageAccess
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.TextMateBootstrap
import javax.inject.Inject

@HiltAndroidApp
class LunarisEditorApp : Application() {

    @Inject lateinit var languageCatalog: LanguageCatalog
    @Inject lateinit var colorSchemeCatalog: ColorSchemeCatalog
    @Inject lateinit var recentsStore: RecentsStore
    @Inject lateinit var storageAccess: StorageAccess

    override fun onCreate() {
        super.onCreate()
        TextMateBootstrap.ensureRegistered(this, colorSchemeCatalog)
        // Grammar parsing is the heaviest startup work: keep it off the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            TextMateBootstrap.loadGrammars(languageCatalog)
        }
        // One-shot cleanup of recents that can no longer be opened (expired grants / deleted files).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { recentsStore.prune { storageAccess.isUsableUri(it) } }
            runCatching { prepareShareDir(cacheDir) }   // drop share copies left from the last run
        }
    }
}
