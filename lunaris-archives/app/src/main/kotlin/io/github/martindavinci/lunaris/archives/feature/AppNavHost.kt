package io.github.martindavinci.lunaris.archives.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.extract.SafTarget
import io.github.martindavinci.lunaris.archives.feature.browser.BrowserRoute
import io.github.martindavinci.lunaris.archives.feature.browser.ExtractArgs
import io.github.martindavinci.lunaris.archives.feature.extract.ExtractRoute

/** Home (for now a single "Open an archive" button) and the browser. */
@Composable
fun AppNavHost(
    onOpenRequest: (Uri) -> String,
    lastTree: () -> Uri?,
    openTree: (Uri) -> SafTarget,
    startExtraction: (ExtractArgs, SafTarget) -> Boolean,
) {
    val nav = rememberNavController()
    var pendingExtract by remember { mutableStateOf<ExtractArgs?>(null) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val args = pendingExtract
        pendingExtract = null
        if (uri != null && args != null && startExtraction(args, openTree(uri))) nav.navigate(Routes.EXTRACT)
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                OpenArchiveStart { uri -> nav.navigate(Routes.browser(onOpenRequest(uri))) }
            }
            composable(Routes.BROWSER, arguments = listOf(navArgument(Routes.ARG_ARCHIVE_ID) { type = NavType.StringType })) { entry ->
                BrowserRoute(
                    onExit = { if (nav.currentBackStackEntry == entry) nav.popBackStack() },
                    onNested = { id -> nav.navigate(Routes.browser(id)) },
                    onExtract = { args ->
                        pendingExtract = args
                        treePicker.launch(lastTree())
                    },
                )
            }
            composable(Routes.EXTRACT) { entry ->
                ExtractRoute(onExit = { if (nav.currentBackStackEntry == entry) nav.popBackStack() }, openTree = openTree)
            }
        }
    }
}

@Composable
private fun OpenArchiveStart(onPicked: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TextButton(onClick = { picker.launch(arrayOf(ANY_TYPE)) }) { Text(stringResource(R.string.home_open_archive)) }
    }
}

private const val ANY_TYPE = "*/*"
