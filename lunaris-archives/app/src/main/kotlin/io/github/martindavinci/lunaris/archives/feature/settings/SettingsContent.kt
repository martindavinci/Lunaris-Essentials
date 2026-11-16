package io.github.martindavinci.lunaris.archives.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.BuildConfig
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.iconTile

private val ListBottomPadding = 24.dp
private val BlockPadding = 16.dp
private val BlockSpacing = 12.dp
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)
private val TileIconSize = 22.dp
private val AboutSpacing = 14.dp
private val AppIconSize = 44.dp
private val AppIconShape = RoundedCornerShape(10.dp)
private val AboutRowMinHeight = 52.dp
private val TrailingIconSize = 20.dp

/**
 * Frame 18. The bar, the section cards and the rows are the ones copied from lunaris-pdf
 * (`SettingsKit.kt`); what is new here is the theme chosen in place, the size of the temporary
 * files, and the About card of the design. [language] is the name of the language in use.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    language: String,
    onAction: (SettingsAction) -> Unit,
    onLanguage: () -> Unit,
    onLicences: () -> Unit,
    onSource: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = ListBottomPadding)) {
        SettingsTopBar(stringResource(R.string.settings_title), onBack)
        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            ThemeBlock(state.settings.theme, onSelect = { onAction(SettingsAction.SetTheme(it)) })
            HorizontalDivider(color = colors.surfaceVariant)
            SettingsRow(Icons.Rounded.Translate, stringResource(R.string.settings_language), language, onClick = onLanguage) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        SettingsSection(stringResource(R.string.settings_section_files)) {
            val summary = stringResource(R.string.settings_clear_temp_summary)
            SettingsRow(
                icon = Icons.Rounded.DeleteSweep,
                title = stringResource(R.string.settings_clear_temp),
                subtitle = if (state.tempBytes > 0) summary + SEPARATOR + sizeText(state.tempBytes) else summary,
                onClick = { onAction(SettingsAction.ClearTemporaryFiles) },
            )
            HorizontalDivider(color = colors.surfaceVariant)
            SettingsToggleRow(Icons.Rounded.History, stringResource(R.string.settings_remember_recents), null, state.settings.rememberRecents) {
                onAction(SettingsAction.SetRememberRecents(it))
            }
        }
        SettingsSection(stringResource(R.string.settings_section_about)) {
            Row(Modifier.padding(BlockPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AboutSpacing)) {
                Image(painterResource(R.drawable.lunaris_icon), contentDescription = null, modifier = Modifier.size(AppIconSize).clip(AppIconShape))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME) + SEPARATOR + stringResource(R.string.settings_about_author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            AboutRow(stringResource(R.string.settings_licences), Icons.Rounded.ChevronRight, onLicences)
            AboutRow(stringResource(R.string.settings_source), Icons.AutoMirrored.Rounded.OpenInNew, onSource)
        }
    }
}

/** System / Dark / Light, chosen where it is shown: the design has no dialog for it. */
@Composable
private fun ThemeBlock(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(BlockPadding), verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BlockPadding)) {
            Box(Modifier.size(TileSize).background(colors.iconTile, TileShape), Alignment.Center) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.primary, modifier = Modifier.size(TileIconSize))
            }
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        }
        val modes = listOf(ThemeMode.SYSTEM to R.string.settings_theme_system, ThemeMode.DARK to R.string.settings_theme_dark, ThemeMode.LIGHT to R.string.settings_theme_light)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(selected = current == mode, onClick = { onSelect(mode) }, shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size)) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, trailing: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    HorizontalDivider(color = colors.surfaceVariant)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = AboutRowMinHeight).padding(horizontal = BlockPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(trailing, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(TrailingIconSize))
    }
}
