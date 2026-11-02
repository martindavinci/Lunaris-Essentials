package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.BuildConfig
import io.github.martindavinci.lunaris.scanner.R

private val AboutPadding = 16.dp
private val AboutSpacing = 8.dp

@Composable
internal fun AboutSummary() {
    Column(Modifier.fillMaxWidth().padding(AboutPadding)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AboutSpacing))
        Text(stringResource(R.string.settings_about_tagline), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.settings_about_permissions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
