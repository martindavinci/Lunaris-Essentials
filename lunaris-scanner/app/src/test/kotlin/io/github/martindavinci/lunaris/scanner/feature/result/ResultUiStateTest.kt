package io.github.martindavinci.lunaris.scanner.feature.result

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import org.junit.Test

class ResultUiStateTest {
    private val wifiCode = ScannedCode("WIFI:S:home;T:WPA;P:secret123;;", CodeFormat.QR_CODE)

    @Test fun noCode_isEmpty() {
        assertThat(resultUiState(null, secretRevealed = false)).isEqualTo(ResultUiState())
    }

    @Test fun httpUrl_carriesWarnings_andIsNotBlocked() {
        val state = resultUiState(ScannedCode("http://example.com", CodeFormat.QR_CODE), secretRevealed = false)
        assertThat(state.content).isEqualTo(ParsedContent.Url("http://example.com"))
        assertThat(state.urlWarnings).contains(UrlWarning.NOT_HTTPS)
        assertThat(state.urlBlocked).isFalse()
    }

    @Test fun plainText_hasNoUrlNotices_andShowsRawText() {
        val state = resultUiState(ScannedCode("hello", CodeFormat.QR_CODE), secretRevealed = false)
        assertThat(state.urlWarnings).isEmpty()
        assertThat(state.urlBlocked).isFalse()
        assertThat(state.rawTextHidden).isFalse()
    }

    @Test fun wifiPassword_hidesRawTextUntilRevealed() {
        assertThat(resultUiState(wifiCode, secretRevealed = false).rawTextHidden).isTrue()
        assertThat(resultUiState(wifiCode, secretRevealed = true).rawTextHidden).isFalse()
    }

    @Test fun openWifi_keepsRawTextVisible() {
        val open = ScannedCode("WIFI:S:cafe;T:nopass;;", CodeFormat.QR_CODE)
        assertThat(resultUiState(open, secretRevealed = false).rawTextHidden).isFalse()
    }

    @Test fun wifiWithoutAnSsid_masksThePasswordEverywhere() {
        val state = resultUiState(ScannedCode("WIFI:S:;T:WPA;P:secret;;", CodeFormat.QR_CODE), secretRevealed = false)

        assertThat(state.rawTextHidden).isTrue()
        assertThat(state.fields).contains(ResultField.Secret(FieldLabel.PASSWORD, "secret"))
        assertThat(state.fields.map { it.label }).doesNotContain(FieldLabel.SSID)
        assertThat(state.actions).contains(ResultAction.Copy("WIFI:S:;T:WPA;P:secret;;", sensitive = true))
        assertThat(state.actions.filterIsInstance<ResultAction.ConnectWifi>()).isEmpty()
    }

    @Test fun openWifiWithAStrayPassword_masksItEverywhere() {
        val text = "WIFI:S:cafe;T:nopass;P:secret;;"
        val state = resultUiState(ScannedCode(text, CodeFormat.QR_CODE), secretRevealed = false)

        assertThat(state.rawTextHidden).isTrue()
        assertThat(state.fields).contains(ResultField.Secret(FieldLabel.PASSWORD, "secret"))
        assertThat(state.actions).contains(ResultAction.Copy(text, sensitive = true))
    }
}
