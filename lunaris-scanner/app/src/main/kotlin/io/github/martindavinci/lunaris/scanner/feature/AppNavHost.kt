package io.github.martindavinci.lunaris.scanner.feature

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.martindavinci.lunaris.scanner.feature.scan.ScanRoute
import io.github.martindavinci.lunaris.scanner.feature.settings.SettingsRoute

/**
 * The tabs and the full screen Result route. With [startAtResult] the app was opened to read a
 * shared image: Result is alone on the back stack and leaving it calls [onExit], which returns to
 * the app that shared the image.
 *
 * System bar insets are applied and consumed here, so routes do not handle them again.
 */
@Composable
fun AppNavHost(startAtResult: Boolean, onExit: () -> Unit) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            if (currentRoute in TopLevelDestination.routes) {
                AppBottomBar(currentRoute = currentRoute, onSelect = nav::navigateToTab)
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
                    onCodeScanned = openResult,
                    onDocumentStarted = { nav.navigate(Routes.DOCUMENT) { launchSingleTop = true } },
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
