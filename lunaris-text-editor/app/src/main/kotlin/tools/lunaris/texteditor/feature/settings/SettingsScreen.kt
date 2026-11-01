package tools.lunaris.texteditor.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfigDialog
import tools.lunaris.texteditor.settings.AppLocales
import tools.lunaris.texteditor.settings.AppTheme
import tools.lunaris.texteditor.settings.BackupCodec
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.Suggestions

internal enum class SettingChoice {
    ENCODING, LINE_BREAK, INDENT, SUGGESTIONS, FONT_TYPE, FONT_SIZE, LINE_SPACING, THEME, AUTOSAVE_INTERVAL
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
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
    val toolbarConfig by viewModel.toolbar().collectAsStateWithLifecycle(initialValue = ToolbarConfig.DEFAULT)
    // A language change restarts the activity, so reading it once is enough.
    val language = remember { AppLocales.current(context) }

    SettingsContent(
        s = s,
        language = language,
        fileFilterOn = fileFilterOn,
        toolbarConfig = toolbarConfig,
        onBack = onBack,
        onUpdate = viewModel::update,
        onSetFileFilter = viewModel::setFileFilter,
        onSetToolbar = viewModel::setToolbar,
        onSetLanguage = { tag -> (context as? Activity)?.let { AppLocales.set(it, tag) } },
        onOpenLicenses = onOpenLicenses,
        onBackup = { backupLauncher.launch(context.getString(R.string.backup_default_name)) },
        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
    )
}

/** Stateless Settings UI. Holds dialog/toolbar visibility locally so the screen
 *  is fully renderable in a preview without a ViewModel. */
@Composable
internal fun SettingsContent(
    s: EditorSettings,
    language: String,
    fileFilterOn: Boolean,
    toolbarConfig: ToolbarConfig,
    onBack: () -> Unit,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onSetFileFilter: (Boolean) -> Unit,
    onSetToolbar: (ToolbarConfig) -> Unit,
    onSetLanguage: (String) -> Unit,
    onOpenLicenses: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var openChoice by remember { mutableStateOf<SettingChoice?>(null) }
    var showToolbar by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var openCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }

    if (!LocalInspectionMode.current) {
        BackHandler(enabled = openCategory != null) { openCategory = null }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (openCategory != null) openCategory = null else onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(
                        text = openCategory?.let { categoryTitle(it) } ?: stringResource(R.string.settings_title),
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
            when (openCategory) {
                null -> SettingsHub(onOpen = { openCategory = it })
                SettingsCategory.EDITOR -> EditorCategory(
                    s = s,
                    onUpdate = onUpdate,
                    onIndentClick = { openChoice = SettingChoice.INDENT },
                    onLineBreakClick = { openChoice = SettingChoice.LINE_BREAK },
                    onShowToolbar = { showToolbar = true },
                )
                SettingsCategory.DISPLAY -> DisplayCategory(
                    s = s,
                    language = language,
                    onUpdate = onUpdate,
                    onLanguageClick = { showLanguage = true },
                    onThemeClick = { openChoice = SettingChoice.THEME },
                    onFontTypeClick = { openChoice = SettingChoice.FONT_TYPE },
                    onFontSizeClick = { openChoice = SettingChoice.FONT_SIZE },
                    onLineSpacingClick = { openChoice = SettingChoice.LINE_SPACING },
                )
                SettingsCategory.KEYBOARD -> KeyboardCategory(
                    s = s,
                    onUpdate = onUpdate,
                    onSuggestionsClick = { openChoice = SettingChoice.SUGGESTIONS },
                )
                SettingsCategory.FILES -> FilesCategory(
                    s = s,
                    fileFilterOn = fileFilterOn,
                    onUpdate = onUpdate,
                    onSetFileFilter = onSetFileFilter,
                    onEncodingClick = { openChoice = SettingChoice.ENCODING },
                    onIntervalClick = { openChoice = SettingChoice.AUTOSAVE_INTERVAL },
                )
                SettingsCategory.INFO -> InfoCategory(
                    onOpenLicenses = onOpenLicenses,
                    onBackup = onBackup,
                    onRestore = onRestore,
                )
            }
        }
    }

    SettingsChoiceDialogs(
        openChoice = openChoice,
        s = s,
        onUpdate = onUpdate,
        onDismiss = { openChoice = null },
    )

    if (showLanguage) {
        LanguageDialog(current = language, onSelect = onSetLanguage, onDismiss = { showLanguage = false })
    }

    if (showToolbar) {
        ToolbarConfigDialog(
            initial = toolbarConfig,
            onApply = onSetToolbar,
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
