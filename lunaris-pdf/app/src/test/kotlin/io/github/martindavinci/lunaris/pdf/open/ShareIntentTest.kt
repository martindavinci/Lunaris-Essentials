package io.github.martindavinci.lunaris.pdf.open

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareIntentTest {
    private companion object {
        const val SEND = "android.intent.action.SEND"
        const val SEND_MANY = "android.intent.action.SEND_MULTIPLE"
        const val VIEW = "android.intent.action.VIEW"
    }

    @Test
    fun `several files go to the list`() {
        assertThat(OpenIntent.filesFrom(SEND_MANY, "image/*", listOf("content://a", "content://b")))
            .containsExactly("content://a", "content://b").inOrder()
    }

    @Test
    fun `one image goes to the list too`() {
        assertThat(OpenIntent.filesFrom(SEND, "image/jpeg", listOf("content://a"))).containsExactly("content://a")
    }

    @Test
    fun `one pdf is for reading, not for the list`() {
        assertThat(OpenIntent.filesFrom(SEND, "application/pdf", listOf("content://a"))).isEmpty()
    }

    @Test
    fun `only content, as a share could point at the app's own files`() {
        assertThat(OpenIntent.filesFrom(SEND_MANY, "image/*", listOf("file:///data/x", "content://b"))).containsExactly("content://b")
    }

    @Test
    fun `other actions have no list`() {
        assertThat(OpenIntent.filesFrom(VIEW, "image/jpeg", listOf("content://a"))).isEmpty()
        assertThat(OpenIntent.filesFrom(null, null, emptyList())).isEmpty()
    }

    @Test
    fun `an image sent is not a document to open`() {
        assertThat(OpenIntent.uriFrom(Intent.ACTION_SEND, "image/jpeg", null, "content://a")).isNull()
        assertThat(OpenIntent.uriFrom(Intent.ACTION_SEND, "application/pdf", null, "content://a")).isEqualTo("content://a")
    }
}
