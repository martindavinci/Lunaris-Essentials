package io.github.martindavinci.lunaris.scanner.feature

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.martindavinci.lunaris.scanner.feature.create.CreateRoute
import io.github.martindavinci.lunaris.scanner.feature.document.DocumentRoute
import io.github.martindavinci.lunaris.scanner.feature.history.HistoryRoute
import io.github.martindavinci.lunaris.scanner.feature.result.ResultRoute
import io.github.martindavinci.lunaris.scanner.feature.scan.ScanRequest
import io.github.martindavinci.lunaris.scanner.feature.scan.ScanRoute
import io.github.martindavinci.lunaris.scanner.feature.settings.SettingsRoute

/**
 * The tabs and the full screen Result route. With [startAtResult] the app was opened to read a
 * shared image: Result is alone on the back stack and leaving it calls [onExit], which returns to
 * the app that shared the image. A [shortcut] from the launcher goes straight where it says.
 *
 * System bar insets are applied and consumed here, so routes do not handle them again.
 */
@Composable
fun AppNavHost(startAtResult: Boolean, shortcut: AppShortcut?, onShortcutHandled: () -> Unit, onExit: () -> Unit) {
    val nav = rememberNavController()
    var scanRequestId by rememberSaveable { mutableIntStateOf(0) }
    var scanRequestCodes by rememberSaveable { mutableStateOf(false) }
    val askScan = { codes: Boolean ->
        scanRequestId++
        scanRequestCodes = codes
    }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            if (currentRoute in TopLevelDestination.routes) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        if (route == Routes.SCAN) askScan(false)
                        nav.navigateToTab(route)
                    },
                )
            }
        },
    ) { padding ->
        val openResult = { nav.navigate(Routes.RESULT) { launchSingleTop = true } }
        NavHost(
            navController = nav,
            startDestination = if (startAtResult) Routes.RESULT else Routes.SCAN,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.SCAN) {
                ScanRoute(
                    request = ScanRequest(scanRequestId, scanRequestCodes).takeIf { scanRequestId > 0 },
                    onOpenResult = openResult,
                    onDocumentChosen = { nav.navigate(Routes.DOCUMENT) { launchSingleTop = true } },
                )
            }
            composable(Routes.DOCUMENT) { entry ->
                DocumentRoute(onClose = { if (nav.currentBackStackEntry == entry) nav.popBackStack() })
            }
            composable(Routes.HISTORY) { HistoryRoute(onOpenEntry = openResult) }
            composable(Routes.CREATE) { CreateRoute() }
            composable(Routes.SETTINGS) { SettingsRoute() }
            composable(Routes.RESULT) { entry ->
                // A popped screen stays on top and clickable until its exit animation ends: a second
                // tap on its back arrow must not act on the screen below.
                ResultRoute(
                    onBack = {
                        if (nav.currentBackStackEntry == entry) {
                            if (nav.previousBackStackEntry == null) onExit() else nav.popBackStack()
                        }
                    },
                )
            }
        }
        // Here, after the host: the graph must be there to navigate in it.
        LaunchedEffect(shortcut) {
            when (shortcut) {
                AppShortcut.SCAN_CODE -> {
                    askScan(true)
                    nav.navigateToTab(Routes.SCAN)
                }
                AppShortcut.SCAN_DOCUMENT -> {
                    nav.navigateToTab(Routes.SCAN)
                    nav.navigate(Routes.DOCUMENT) { launchSingleTop = true }
                }
                AppShortcut.HISTORY -> nav.navigateToTab(Routes.HISTORY)
                null -> return@LaunchedEffect
            }
            onShortcutHandled()
        }
    }
}

object Routes {
    const val SCAN = "scan"
    const val HISTORY = "history"
    const val CREATE = "create"
    const val SETTINGS = "settings"
    const val RESULT = "result"
    const val DOCUMENT = "document"
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
