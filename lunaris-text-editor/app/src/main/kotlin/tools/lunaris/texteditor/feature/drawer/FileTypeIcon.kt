package tools.lunaris.texteditor.feature.drawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Html
import androidx.compose.ui.graphics.vector.ImageVector

/** A coarse icon for a file, chosen by its extension. */
fun fileTypeIcon(name: String): ImageVector = when (name.substringAfterLast('.', "").lowercase()) {
    "html", "htm", "xml" -> Icons.Filled.Html
    "js", "ts", "kt", "java", "py", "c", "cpp", "cs", "go", "rs", "rb", "php", "sh" -> Icons.Filled.Code
    "json", "yaml", "yml", "toml" -> Icons.Filled.DataObject
    "md", "txt", "rst" -> Icons.AutoMirrored.Filled.Article
    else -> Icons.Filled.Description
}
