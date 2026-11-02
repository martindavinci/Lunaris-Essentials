package io.github.martindavinci.lunaris.scanner

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LaunchIntentsTest {
    @Test fun firstCreate_handlesIntent() {
        assertThat(isFreshLaunchIntent(recreated = false, flags = 0)).isTrue()
    }

    @Test fun recreatedAfterRotation_doesNotReHandleIntent() {
        assertThat(isFreshLaunchIntent(recreated = true, flags = 0)).isFalse()
    }

    @Test fun relaunchedFromRecents_doesNotReHandleStaleIntent() {
        assertThat(isFreshLaunchIntent(recreated = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).isFalse()
    }
}
