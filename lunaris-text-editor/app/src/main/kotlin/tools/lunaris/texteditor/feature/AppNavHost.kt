package tools.lunaris.texteditor.feature

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tools.lunaris.texteditor.feature.editor.EditorScreen
import tools.lunaris.texteditor.feature.settings.FileAssociationScreen

object Routes {
    const val EDITOR = "editor"
    const val ASSOCIATIONS = "associations"
    const val STORAGE = "storage"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val PREVIEW = "preview"
}

/** A file the app was asked to open via a VIEW/EDIT intent. [readOnly] when no write grant. */
data class OpenRequest(val uri: String, val readOnly: Boolean = false)

@Composable
fun AppNavHost(openRequest: OpenRequest? = null) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.EDITOR) {
        composable(Routes.EDITOR) {
            EditorScreen(onNavigate = { route -> nav.navigate(route) }, openRequest = openRequest)
        }
        composable(Routes.ASSOCIATIONS) {
            FileAssociationScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.STORAGE) {
            tools.lunaris.texteditor.feature.browser.StorageManagerScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            tools.lunaris.texteditor.feature.settings.SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAssociations = { nav.navigate(Routes.ASSOCIATIONS) },
                onOpenLicenses = { nav.navigate(Routes.LICENSES) },
            )
        }
        composable(Routes.LICENSES) {
            tools.lunaris.texteditor.feature.settings.LicensesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PREVIEW) {
            tools.lunaris.texteditor.feature.preview.PreviewScreen(onBack = { nav.popBackStack() })
        }
    }
}
