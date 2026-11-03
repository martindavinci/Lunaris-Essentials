package io.github.martindavinci.lunaris.pdf.open

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenIntentTest {
    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
        const val PDF = "application/pdf"
    }

    @Test fun view_usesItsData() {
        assertThat(OpenIntent.uriFrom(VIEW, PDF, "content://a/1", null)).isEqualTo("content://a/1")
    }

    @Test fun view_acceptsAFile() {
        assertThat(OpenIntent.uriFrom(VIEW, PDF, "file:///sdcard/a.pdf", null)).isEqualTo("file:///sdcard/a.pdf")
    }

    @Test fun view_refusesAnythingElse() {
        assertThat(OpenIntent.uriFrom(VIEW, PDF, "https://example.com/a.pdf", null)).isNull()
        assertThat(OpenIntent.uriFrom(VIEW, PDF, "javascript:alert(1)", null)).isNull()
        assertThat(OpenIntent.uriFrom(VIEW, PDF, null, null)).isNull()
    }

    @Test fun send_usesItsStream() {
        assertThat(OpenIntent.uriFrom(SEND, PDF, null, "content://a/2")).isEqualTo("content://a/2")
    }

    @Test fun nonContentSchemeFromSend_isRefused() {
        assertThat(OpenIntent.uriFrom(SEND, PDF, null, "file:///data/data/x/secret.pdf")).isNull()
        assertThat(OpenIntent.uriFrom(SEND, PDF, null, null)).isNull()
    }

    @Test fun otherActions_openNothing() {
        assertThat(OpenIntent.uriFrom("android.intent.action.MAIN", null, null, null)).isNull()
        assertThat(OpenIntent.uriFrom(null, PDF, "content://a/1", "content://a/1")).isNull()
    }

    @Test fun launchIntent_isActedOnOnce() {
        assertThat(OpenIntent.isFresh(recreated = false, flags = 0)).isTrue()
        assertThat(OpenIntent.isFresh(recreated = true, flags = 0)).isFalse()
        assertThat(OpenIntent.isFresh(recreated = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).isFalse()
    }
}
