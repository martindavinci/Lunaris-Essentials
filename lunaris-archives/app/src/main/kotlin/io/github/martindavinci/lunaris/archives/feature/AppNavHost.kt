package io.github.martindavinci.lunaris.archives.feature

import androidx.compose.ui.platform.LocalContext
import io.github.martindavinci.lunaris.archives.ui.sayNoPicker
import io.github.martindavinci.lunaris.archives.ui.launchOr
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.martindavinci.lunaris.archives.StartTarget
import io.github.martindavinci.lunaris.archives.extract.SafTarget
import io.github.martindavinci.lunaris.archives.feature.browser.BrowserRoute
import io.github.martindavinci.lunaris.archives.feature.browser.ExtractArgs
import io.github.martindavinci.lunaris.archives.feature.create.CreateRoute
import io.github.martindavinci.lunaris.archives.feature.extract.ExtractRoute
import io.github.martindavinci.lunaris.archives.feature.home.HomeRoute
import io.github.martindavinci.lunaris.archives.feature.settings.SettingsRoute

/**
 * Home, the browser, extraction, New archive and Settings. A screen another app opened ([target])
 * goes on top of Home; leaving it from its root goes back to that app ([onExit]), not to Home.
 */
@Composable
fun AppNavHost(
    target: StartTarget?,
    onTargetShown: () -> Unit,
    onExit: () -> Unit,
    lastTree: () -> Uri?,
    openTree: (Uri) -> SafTarget,
    startExtraction: (ExtractArgs, SafTarget) -> Boolean,
) {
    val nav = rememberNavController()
    var pendingExtract by remember { mutableStateOf<ExtractArgs?>(null) }
    var fromOtherApp by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val args = pendingExtract
        pendingExtract = null
        if (uri != null && args != null && startExtraction(args, openTree(uri))) nav.navigate(Routes.EXTRACT)
    }
    LaunchedEffect(target) {
        val move = target ?: return@LaunchedEffect
        onTargetShown()
        val route = when (move) {
            is StartTarget.Browser -> Routes.browser(move.requestId)
            StartTarget.Create -> Routes.CREATE
        }
        fromOtherApp = route
        nav.navigate(route) { popUpTo(Routes.HOME) }
    }
    /** Back from [entry] (shown as [route]): to the other app if it opened this screen, else one step. */
    fun leave(entry: NavBackStackEntry, route: String) {
        if (nav.currentBackStackEntry != entry) return
        if (fromOtherApp == route) {
            fromOtherApp = null
            onExit()
        } else {
            nav.popBackStack()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeRoute(
                    onBrowse = { id -> nav.navigate(Routes.browser(id)) },
                    onExtractAll = { id -> nav.navigate(Routes.browser(id)) },
                    onCreate = { nav.navigate(Routes.CREATE) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) { entry ->
                SettingsRoute(onBack = { leave(entry, Routes.SETTINGS) })
            }
            composable(Routes.CREATE) { entry ->
                CreateRoute(
                    onClose = { leave(entry, Routes.CREATE) },
                    onOpenArchive = { id ->
                        if (fromOtherApp == Routes.CREATE) fromOtherApp = Routes.browser(id)
                        nav.navigate(Routes.browser(id)) { popUpTo(Routes.CREATE) { inclusive = true } }
                    },
                )
            }
            composable(Routes.BROWSER, arguments = listOf(navArgument(Routes.ARG_ARCHIVE_ID) { type = NavType.StringType })) { entry ->
                val route = Routes.browser(entry.arguments?.getString(Routes.ARG_ARCHIVE_ID).orEmpty())
                BrowserRoute(
                    onExit = { leave(entry, route) },
                    onNested = { id -> nav.navigate(Routes.browser(id)) },
                    onExtract = { args ->
                        pendingExtract = args
                        treePicker.launchOr(lastTree()) {
                            pendingExtract = null
                            context.sayNoPicker()
                        }
                    },
                )
            }
            composable(Routes.EXTRACT) { entry ->
                ExtractRoute(onExit = { leave(entry, Routes.EXTRACT) }, openTree = openTree)
            }
        }
    }
}
