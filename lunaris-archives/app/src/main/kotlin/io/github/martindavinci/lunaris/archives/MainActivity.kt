package io.github.martindavinci.lunaris.archives

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.martindavinci.lunaris.archives.engine.EngineProcess
import io.github.martindavinci.lunaris.archives.extract.ExtractStarter
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.extract.TreeTargets
import io.github.martindavinci.lunaris.archives.feature.AppNavHost
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.session.PasswordMemory
import io.github.martindavinci.lunaris.archives.files.CacheJanitor
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var cacheJanitor: CacheJanitor
    @Inject lateinit var session: ArchiveSession
    @Inject lateinit var passwordMemory: PasswordMemory
    @Inject lateinit var extractMarker: PrefsExtractMarker
    @Inject lateinit var treeTargets: TreeTargets
    @Inject lateinit var extractStarter: ExtractStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sweepOncePerProcess()
        enableEdgeToEdge()
        setContent {
            LunarisArchivesTheme {
                AppNavHost(
                    onOpenRequest = { uri -> session.register(OpenRequest(uri.toString(), null, null, null)) },
                    lastTree = treeTargets::last,
                    openTree = treeTargets::open,
                    startExtraction = { args, target -> extractStarter.start(args.archiveId, args.indices, target) },
                )
            }
        }
    }

    /** "Remember until I close the app" ends here; a rotation is not closing. */
    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) passwordMemory.forgetAll()
        super.onDestroy()
    }

    /** Old temp copies go at start-up, in the main process only (never in the isolated engine). */
    private fun sweepOncePerProcess() {
        if (swept || EngineProcess.isCurrent()) return
        swept = true
        lifecycleScope.launch(Dispatchers.IO) {
            cacheJanitor.sweep(System.currentTimeMillis())
            extractMarker.recover { token -> DocumentsContract.deleteDocument(contentResolver, Uri.parse(token)) }
        }
    }

    private companion object {
        @Volatile var swept = false
    }
}
