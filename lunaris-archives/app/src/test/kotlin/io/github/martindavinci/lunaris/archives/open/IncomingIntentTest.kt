package io.github.martindavinci.lunaris.archives.open

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IncomingIntentTest {
    private val archives = setOf("content://a/zip")
    private fun c(action: String?, data: String? = null, streams: List<String> = emptyList()) =
        IncomingIntent.classify(action, data, streams) { it in archives }

    @Test fun viewOfContent_opens() { assertThat(c(ACTION_VIEW, "content://x/1")).isEqualTo(Incoming.OpenArchive("content://x/1")) }
    @Test fun viewOfFileScheme_isIgnored() { assertThat(c(ACTION_VIEW, "file:///sdcard/a.zip")).isEqualTo(Incoming.Nothing) }
    @Test fun viewWithoutData_isIgnored() { assertThat(c(ACTION_VIEW)).isEqualTo(Incoming.Nothing) }
    @Test fun sendOfAnArchive_opens() { assertThat(c(ACTION_SEND, streams = listOf("content://a/zip"))).isEqualTo(Incoming.OpenArchive("content://a/zip")) }
    @Test fun sendOfOtherFiles_creates() {
        assertThat(c(ACTION_SEND, streams = listOf("content://p/1"))).isEqualTo(Incoming.CreateFrom(listOf("content://p/1")))
        assertThat(c(ACTION_SEND_MULTIPLE, streams = listOf("content://a/zip", "content://p/1"))).isEqualTo(Incoming.CreateFrom(listOf("content://a/zip", "content://p/1")))
    }
    @Test fun nonContentStreams_areDropped() {
        assertThat(c(ACTION_SEND_MULTIPLE, streams = listOf("file:///data/data/x", "content://p/1"))).isEqualTo(Incoming.CreateFrom(listOf("content://p/1")))
    }
    @Test fun repeatedStreams_countOnce() {
        assertThat(c(ACTION_SEND_MULTIPLE, streams = listOf("content://p/1", "content://p/1"))).isEqualTo(Incoming.CreateFrom(listOf("content://p/1")))
    }
    @Test fun sendWithoutStreams_isTextOnly() {
        assertThat(c(ACTION_SEND)).isEqualTo(Incoming.TextOnly)
        assertThat(c(ACTION_SEND_MULTIPLE, streams = listOf("file:///x"))).isEqualTo(Incoming.TextOnly)
    }
    @Test fun otherActions_doNothing() {
        assertThat(c("android.intent.action.MAIN")).isEqualTo(Incoming.Nothing)
        assertThat(c(null)).isEqualTo(Incoming.Nothing)
    }

    private companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    }
}
