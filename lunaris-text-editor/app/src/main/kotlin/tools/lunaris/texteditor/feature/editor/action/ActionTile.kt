package tools.lunaris.texteditor.feature.editor.action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.feature.editor.EditorMenuAction
import tools.lunaris.texteditor.feature.editor.icon

private val TileWidth = 76.dp
private val TileIconSize = 24.dp
private val DisabledAlpha = 0.38f

/** One icon-over-label tile in the action sheet grid. */
@Composable
fun ActionTile(
    action: EditorMenuAction,
    enabled: Boolean,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
    Column(
        modifier = Modifier
            .width(TileWidth)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(percent = 30), color = bg) {
            Icon(
                action.icon(),
                contentDescription = null,
                tint = content,
                modifier = Modifier.padding(10.dp).size(TileIconSize),
            )
        }
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
