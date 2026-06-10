package tools.lunaris.texteditor.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfigDialog
import tools.lunaris.texteditor.settings.AppTheme
import tools.lunaris.texteditor.settings.BackupCodec
import tools.lunaris.texteditor.settings.Suggestions

internal enum class SettingChoice {
    ENCODING, LINE_BREAK, INDENT, SUGGESTIONS, FONT_TYPE, FONT_SIZE, LINE_SPACING, THEME, AUTOSAVE_INTERVAL
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAssociations: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val json = BackupCodec.toJson(viewModel.buildBackup())
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (json != null) viewModel.applyBackup(BackupCodec.fromJson(json))
        }
    }

    val s by viewModel.settings.collectAsStateWithLifecycle()
    val fileFilterOn by viewModel.fileFilter().collectAsStateWithLifecycle(initialValue = true)
    var openChoice by remember { mutableStateOf<SettingChoice?>(null) }
    var showToolbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            GeneralSection(
                s = s,
                fileFilterOn = fileFilterOn,
                onUpdate = viewModel::update,
                onSetFileFilter = viewModel::setFileFilter,
                onOpenAssociations = onOpenAssociations,
                onShowToolbar = { showToolbar = true },
                onEncodingClick = { openChoice = SettingChoice.ENCODING },
                onLineBreakClick = { openChoice = SettingChoice.LINE_BREAK },
                onIndentClick = { openChoice = SettingChoice.INDENT },
            )
            InputSection(
                s = s,
                onUpdate = viewModel::update,
                onSuggestionsClick = { openChoice = SettingChoice.SUGGESTIONS },
            )
            DisplaySection(
                s = s,
                onUpdate = viewModel::update,
                onFontTypeClick = { openChoice = SettingChoice.FONT_TYPE },
                onFontSizeClick = { openChoice = SettingChoice.FONT_SIZE },
                onLineSpacingClick = { openChoice = SettingChoice.LINE_SPACING },
            )
            AutosaveSection(
                s = s,
                onUpdate = viewModel::update,
                onIntervalClick = { openChoice = SettingChoice.AUTOSAVE_INTERVAL },
            )
            AppearanceSection(
                s = s,
                onUpdate = viewModel::update,
                onThemeClick = { openChoice = SettingChoice.THEME },
            )
            AboutSection(
                onOpenLicenses = onOpenLicenses,
                onBackup = { backupLauncher.launch(context.getString(R.string.backup_default_name)) },
                onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
            )
        }
    }

    SettingsChoiceDialogs(
        openChoice = openChoice,
        s = s,
        onUpdate = viewModel::update,
        onDismiss = { openChoice = null },
    )

    if (showToolbar) {
        val toolbarConfig by viewModel.toolbar().collectAsStateWithLifecycle(initialValue = ToolbarConfig.DEFAULT)
        ToolbarConfigDialog(
            initial = toolbarConfig,
            onApply = viewModel::setToolbar,
            onDismiss = { showToolbar = false },
        )
    }
}

@Composable
internal fun indentLabel(indentUnit: String): String = when (indentUnit) {
    "\t" -> stringResource(R.string.indent_tab)
    "  " -> stringResource(R.string.indent_2)
    "    " -> stringResource(R.string.indent_4)
    "        " -> stringResource(R.string.indent_8)
    else -> indentUnit
}

@Composable
internal fun suggestionLabel(suggestions: Suggestions): String = when (suggestions) {
    Suggestions.ON -> stringResource(R.string.suggestions_on)
    Suggestions.OFF -> stringResource(R.string.suggestions_off)
    Suggestions.OFF_AGGRESSIVE -> stringResource(R.string.suggestions_off_aggressive)
}

@Composable
internal fun themeLabel(appTheme: AppTheme): String = when (appTheme) {
    AppTheme.LIGHT -> stringResource(R.string.theme_light)
    AppTheme.DARK -> stringResource(R.string.theme_dark)
    AppTheme.BLACK -> stringResource(R.string.theme_black)
}
