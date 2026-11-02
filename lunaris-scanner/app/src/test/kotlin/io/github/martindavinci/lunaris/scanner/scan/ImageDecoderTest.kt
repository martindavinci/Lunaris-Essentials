package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageDecoderTest {
    @Test fun largePhoto_triesTwoDownscaledSizes() {
        assertThat(decodeSampleSizes(width = 4000, height = 3000)).containsExactly(2, 4).inOrder()
    }

    @Test fun mediumImage_triesFullSizeThenSmaller() {
        assertThat(decodeSampleSizes(width = 1600, height = 1200)).containsExactly(1, 2).inOrder()
    }

    @Test fun smallImage_isDecodedOnce() {
        assertThat(decodeSampleSizes(width = 800, height = 600)).containsExactly(1)
    }

    @Test fun unreadableBounds_areNotAnImage() {
        assertThat(decodeSampleSizes(width = -1, height = -1)).isEmpty()
        assertThat(decodeSampleSizes(width = 0, height = 100)).isEmpty()
    }

    @Test fun contentOfAnotherApp_isRead() {
        assertThat(isReadableSource("content", "media", OWN_PACKAGE)).isTrue()
        assertThat(isReadableSource("content", "com.other.provider", OWN_PACKAGE)).isTrue()
    }

    @Test fun anythingElse_isNot() {
        assertThat(isReadableSource("file", null, OWN_PACKAGE)).isFalse()
        assertThat(isReadableSource("file", "", OWN_PACKAGE)).isFalse()
        assertThat(isReadableSource("http", "example.com", OWN_PACKAGE)).isFalse()
        assertThat(isReadableSource("content", null, OWN_PACKAGE)).isFalse()
        assertThat(isReadableSource(null, null, OWN_PACKAGE)).isFalse()
    }

    @Test fun providersOfThisApp_areNot() {
        assertThat(isReadableSource("content", "$OWN_PACKAGE.fileprovider", OWN_PACKAGE)).isFalse()
        assertThat(isReadableSource("content", "0@$OWN_PACKAGE.fileprovider", OWN_PACKAGE)).isFalse()
    }

    private companion object {
        const val OWN_PACKAGE = "io.github.martindavinci.lunaris.scanner"
    }
}
