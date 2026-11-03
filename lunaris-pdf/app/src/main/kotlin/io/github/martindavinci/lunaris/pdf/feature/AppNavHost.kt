package io.github.martindavinci.lunaris.pdf.feature

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderRoute
import io.github.martindavinci.lunaris.pdf.feature.recents.RecentsRoute
import io.github.martindavinci.lunaris.pdf.feature.settings.SettingsRoute
import kotlinx.coroutines.flow.Flow

/**
 * With [startAtReader] a file from another app started this app: the Reader is the only screen, and
 * leaving it calls [onExit], which returns to that app. Nothing stands between opening and reading.
 */
@Composable
fun AppNavHost(startAtReader: Boolean, readerRequests: Flow<Unit>, onExit: () -> Unit) {
    val nav = rememberNavController()
    val start = rememberSaveable { if (startAtReader) Routes.READER else Routes.RECENTS }
    LaunchedEffect(nav) { readerRequests.collect { nav.openReader() } }
    Surface {
        NavHost(navController = nav, startDestination = start) {
            composable(Routes.RECENTS) {
                RecentsRoute(onOpenReader = nav::openReader, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) { entry ->
                SettingsRoute(onBack = { if (nav.currentBackStackEntry == entry) nav.popBackStack() })
            }
            composable(Routes.READER) { entry ->
                // A popped screen stays clickable until its exit animation ends: a second tap on its
                // back arrow must not act on the screen below.
                ReaderRoute(
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
    const val RECENTS = "recents"
    const val READER = "reader"
    const val SETTINGS = "settings"
}

/** A new Reader for the new document, replacing the one on screen if any. */
private fun NavHostController.openReader() {
    navigate(Routes.READER) {
        popUpTo(Routes.READER) { inclusive = true }
    }
}
