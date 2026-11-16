package io.github.martindavinci.lunaris.archives

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.martindavinci.lunaris.archives.engine.EngineProcess
import io.github.martindavinci.lunaris.archives.files.CacheJanitor
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var cacheJanitor: CacheJanitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sweepOncePerProcess()
        enableEdgeToEdge()
        setContent { LunarisArchivesTheme { Surface(Modifier.fillMaxSize()) {} } }
    }

    /** Old temp copies go at start-up, in the main process only (never in the isolated engine). */
    private fun sweepOncePerProcess() {
        if (swept || EngineProcess.isCurrent()) return
        swept = true
        lifecycleScope.launch(Dispatchers.IO) { cacheJanitor.sweep(System.currentTimeMillis()) }
    }

    private companion object {
        @Volatile var swept = false
    }
}
