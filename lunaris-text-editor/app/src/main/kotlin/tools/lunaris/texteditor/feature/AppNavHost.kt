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
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.EDITOR) {
        composable(Routes.EDITOR) {
            EditorScreen(onNavigate = { route -> nav.navigate(route) })
        }
        composable(Routes.ASSOCIATIONS) {
            FileAssociationScreen(onBack = { nav.popBackStack() })
        }
    }
}
