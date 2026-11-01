package tools.lunaris.texteditor.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.settings.AppLanguage

/** "System default", or the language's name in itself (the same in every UI language). */
@Composable
internal fun languageLabel(tag: String): String = when (tag) {
    "en" -> stringResource(R.string.lang_name_en)
    "de" -> stringResource(R.string.lang_name_de)
    "es" -> stringResource(R.string.lang_name_es)
    "fr" -> stringResource(R.string.lang_name_fr)
    "it" -> stringResource(R.string.lang_name_it)
    "ja" -> stringResource(R.string.lang_name_ja)
    "ko" -> stringResource(R.string.lang_name_ko)
    "nl" -> stringResource(R.string.lang_name_nl)
    "pl" -> stringResource(R.string.lang_name_pl)
    "pt" -> stringResource(R.string.lang_name_pt)
    "zh-CN" -> stringResource(R.string.lang_name_zh_cn)
    else -> stringResource(R.string.language_system)
}

@Composable
internal fun LanguageDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val tags = listOf(AppLanguage.SYSTEM) + AppLanguage.supported
    SingleChoiceDialog(
        title = stringResource(R.string.set_language),
        options = tags.map { languageLabel(it) },
        selectedIndex = tags.indexOf(current).coerceAtLeast(0),
        onSelect = { i -> if (tags[i] != current) onSelect(tags[i]) },
        onDismiss = onDismiss,
    )
}
