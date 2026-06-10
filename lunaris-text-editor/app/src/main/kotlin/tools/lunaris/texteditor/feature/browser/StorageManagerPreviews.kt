package tools.lunaris.texteditor.feature.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

/** Component-level preview: the [StorageManagerScreen] body is VM-driven, so the
 *  stateless top bar is previewed instead of the full screen. */
@Preview(showBackground = true)
@Composable
private fun StorageTopBarPreview() {
    LunarisEditorTheme {
        StorageTopBar(onBack = {})
    }
}
