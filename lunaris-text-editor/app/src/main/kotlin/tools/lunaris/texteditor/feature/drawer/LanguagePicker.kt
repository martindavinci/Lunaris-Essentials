package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.syntax.LanguageDefinition

@Composable
fun LanguagePicker(ext: String, currentLangId: String?, languages: List<LanguageDefinition>, onPick: (String) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            Text(stringResource(R.string.assoc_language_for, ".$ext"), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(query, { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.assoc_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        val filtered = languages.filter { query.isBlank() || it.display.contains(query, ignoreCase = true) }
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { lang ->
                Row(Modifier.fillMaxWidth().clickable { onPick(lang.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (lang.id == currentLangId) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    else Spacer(Modifier.width(24.dp))
                    val faded = lang.scopeName == null // plaintext = no highlighting → greyed
                    Text(lang.display, modifier = Modifier.padding(start = 12.dp),
                        color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
