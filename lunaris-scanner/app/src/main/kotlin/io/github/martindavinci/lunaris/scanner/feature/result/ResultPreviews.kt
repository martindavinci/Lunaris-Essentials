package io.github.martindavinci.lunaris.scanner.feature.result

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

@Composable
private fun PreviewScreen(content: ParsedContent, text: String, format: CodeFormat, urlWarnings: List<UrlWarning> = emptyList()) {
    LunarisScannerTheme {
        ResultScreen(
            state = ResultUiState(
                code = ScannedCode(text, format),
                content = content,
                fields = ResultFields.forContent(content),
                urlWarnings = urlWarnings,
                actions = ResultActions.forContent(content, text),
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onUiAction = {},
            onRun = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultUrlPreview() {
    val url = "http://192.168.1.10:8080/login"
    PreviewScreen(
        content = ParsedContent.Url(url),
        text = url,
        format = CodeFormat.QR_CODE,
        urlWarnings = listOf(UrlWarning.NOT_HTTPS, UrlWarning.IP_ADDRESS_HOST, UrlWarning.UNUSUAL_PORT),
    )
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultWifiPreview() {
    PreviewScreen(
        content = ParsedContent.Wifi("Home", "correct horse", WifiSecurity.WPA, hidden = false),
        text = "WIFI:S:Home;T:WPA;P:correct horse;;",
        format = CodeFormat.QR_CODE,
    )
}

@Preview(showBackground = true)
@Composable
private fun ResultProductPreview() {
    PreviewScreen(
        content = ParsedContent.Product("4006381333931", CodeFormat.EAN_13),
        text = "4006381333931",
        format = CodeFormat.EAN_13,
    )
}

@Preview(showBackground = true)
@Composable
private fun ResultTextPreview() {
    PreviewScreen(content = ParsedContent.Text("Any text at all"), text = "Any text at all", format = CodeFormat.AZTEC)
}
