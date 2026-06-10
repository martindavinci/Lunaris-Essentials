package tools.lunaris.texteditor.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

/** Previews the [PreviewScreen] chrome with the empty-state body.
 *  The real screen renders a WebView, which is not previewable. */
@Preview(showBackground = true)
@Composable
private fun PreviewChromePreview() {
    LunarisEditorTheme {
        PreviewChrome(
            title = "README.md",
            onBack = {},
            onPrint = {},
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Text(
                    text = stringResource(R.string.preview_nothing),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
