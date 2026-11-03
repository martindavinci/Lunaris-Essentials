package io.github.martindavinci.lunaris.pdf.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.BuildConfig
import io.github.martindavinci.lunaris.pdf.R

private const val SEPARATOR = " · "
private val AboutPadding = 16.dp
private val AboutSpacing = 12.dp
private val IconSize = 44.dp
private val IconShape = RoundedCornerShape(10.dp)

@Composable
internal fun AboutCard(onOpenLicenses: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(AboutPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AboutSpacing)) {
            Image(
                painter = painterResource(R.drawable.lunaris_icon),
                contentDescription = null,
                modifier = Modifier.size(IconSize).clip(IconShape),
            )
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME) +
                        SEPARATOR + stringResource(R.string.settings_about_author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(AboutSpacing))
        Text(stringResource(R.string.settings_about_tagline), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.settings_about_permissions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = AboutPadding))
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLicenses).padding(AboutPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
