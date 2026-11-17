package io.github.martindavinci.lunaris.archives

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.martindavinci.lunaris.archives.edits.EditWatcher
import io.github.martindavinci.lunaris.archives.edits.UpdateIo
import io.github.martindavinci.lunaris.archives.edits.UpdateJournal
import io.github.martindavinci.lunaris.archives.engine.EngineProcess
import io.github.martindavinci.lunaris.archives.extract.ExtractStarter
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.extract.TreeTargets
import io.github.martindavinci.lunaris.archives.feature.AppNavHost
import io.github.martindavinci.lunaris.archives.files.CacheJanitor
import io.github.martindavinci.lunaris.archives.open.OpenIntent
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.settings.AppLocales
import io.github.martindavinci.lunaris.archives.ui.theme.isDark
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var cacheJanitor: CacheJanitor
    @Inject lateinit var editWatcher: EditWatcher
    @Inject lateinit var updateJournal: UpdateJournal
    @Inject lateinit var updateIo: UpdateIo
    @Inject lateinit var passwordMemory: PasswordMemory
    @Inject lateinit var session: ArchiveSession
    @Inject lateinit var extractMarker: PrefsExtractMarker
    @Inject lateinit var treeTargets: TreeTargets
    @Inject lateinit var extractStarter: ExtractStarter

    private val viewModel: MainViewModel by viewModels()
    /** This activity was started by the intent it is handling now (so a text-only share closes it again). */
    private var startedByIntent = false

    // Below Android 13 the in-app language is applied here; above, the system does it.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sweepOncePerProcess()
        enableEdgeToEdge()
        val fresh = OpenIntent.isFresh(recreated = savedInstanceState != null, flags = intent?.flags ?: 0)
        startedByIntent = fresh && intent?.action != Intent.ACTION_MAIN
        handle(intent, fresh)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val settings = state.settings ?: return@setContent
            val dark = settings.theme.isDark()
            LaunchedEffect(dark) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            LaunchedEffect(state.textOnly) {
                if (!state.textOnly) return@LaunchedEffect
                viewModel.textOnlyShown()
                Toast.makeText(this@MainActivity, R.string.incoming_text_only, Toast.LENGTH_LONG).show()
                if (startedByIntent) finish()
            }
            LunarisArchivesTheme(themeMode = settings.theme) {
                // A shared file is looked at first, which takes moments, so that Home does not flash by.
                var screensUp by remember { mutableStateOf(false) }
                if (!state.opening || screensUp) {
                    screensUp = true
                    AppNavHost(
                        target = state.target,
                        onTargetShown = viewModel::targetShown,
                        onExit = ::finish,
                        lastTree = treeTargets::last,
                        openTree = treeTargets::open,
                        startExtraction = { args, target -> extractStarter.start(args.archiveId, args.indices, target) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startedByIntent = false
        handle(intent, fresh = true)
    }

    /** Back from another app: a file opened from an archive may have been changed there. */
    override fun onResume() {
        super.onResume()
        if (!EngineProcess.isCurrent()) editWatcher.scanSoon()
    }

    /** "Remember until I close the app" ends here; a rotation is not closing. */
    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            passwordMemory.forgetAll()
            session.closeAll()
        }
        super.onDestroy()
    }

    private fun handle(intent: Intent?, fresh: Boolean) {
        viewModel.handle(intent?.action, intent?.dataString, OpenIntent.streamsOf(intent), fresh)
    }

    /** Old temp copies go at start-up, in the main process only (never in the isolated engine). */
    private fun sweepOncePerProcess() {
        if (swept || EngineProcess.isCurrent()) return
        swept = true
        lifecycleScope.launch(Dispatchers.IO) {
            updateJournal.recover(updateIo)   // an archive left half written by a closed app is put back first
            editWatcher.scan()   // changes made while the app was away are seen before anything is swept
            cacheJanitor.sweep(System.currentTimeMillis())
            extractMarker.recover { token -> DocumentsContract.deleteDocument(contentResolver, Uri.parse(token)) }
        }
    }

    private companion object {
        @Volatile var swept = false
    }
}
