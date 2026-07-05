package tools.lunaris.texteditor.feature

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tools.lunaris.texteditor.feature.editor.EditorScreen

object Routes {
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
}

/**
 * Something the app was asked to open. Either a [uri] (VIEW/EDIT or a shared file) — [readOnly]
 * when no write grant — or inline [sharedText] (ACTION_SEND of plain text, no uri).
 * [nonce] is bumped per intent delivery so re-delivering the SAME content (e.g. tapping the same
 * file in "open with" while the app is already running) still changes this value — otherwise an
 * equal OpenRequest leaves the editor's LaunchedEffect key unchanged and nothing opens.
 */
data class OpenRequest(
    val uri: String? = null,
    val readOnly: Boolean = false,
    val sharedText: String? = null,
    val nonce: Int = 0,
)

@Composable
fun AppNavHost(openRequest: OpenRequest? = null) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.EDITOR) {
        composable(Routes.EDITOR) {
            EditorScreen(onNavigate = { route -> nav.navigate(route) }, openRequest = openRequest)
        }
        composable(Routes.SETTINGS) {
            tools.lunaris.texteditor.feature.settings.SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenLicenses = { nav.navigate(Routes.LICENSES) },
            )
        }
        composable(Routes.LICENSES) {
            tools.lunaris.texteditor.feature.settings.LicensesScreen(onBack = { nav.popBackStack() })
        }
    }
}
