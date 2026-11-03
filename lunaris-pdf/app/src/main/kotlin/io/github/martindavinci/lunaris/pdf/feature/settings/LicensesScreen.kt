package io.github.martindavinci.lunaris.pdf.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's own license, the third-party components and their full license texts. */
private const val NOTICES_ASSET = "licenses/NOTICES.txt"

private val ListHorizontalPadding = 16.dp
private val ListVerticalPadding = 8.dp
private val ParagraphPadding = 6.dp

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    // Paragraphs, so the long text is laid out lazily as it scrolls.
    val paragraphs by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            noticeParagraphs(assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() })
        }
    }
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.settings_licenses), onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = ListHorizontalPadding, vertical = ListVerticalPadding),
        ) {
            items(paragraphs) { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = ParagraphPadding),
                )
            }
        }
    }
}
