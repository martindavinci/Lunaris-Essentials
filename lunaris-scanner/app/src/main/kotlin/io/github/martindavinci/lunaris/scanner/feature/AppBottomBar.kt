package io.github.martindavinci.lunaris.scanner.feature

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

enum class TopLevelDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    SCAN(Routes.SCAN, R.string.nav_scan, Icons.Filled.QrCodeScanner),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    CREATE(Routes.CREATE, R.string.nav_create, Icons.Filled.QrCode2),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings);

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}

@Composable
fun AppBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

@Preview
@Composable
private fun AppBottomBarPreview() {
    LunarisScannerTheme {
        AppBottomBar(currentRoute = Routes.SCAN, onSelect = {})
    }
}
