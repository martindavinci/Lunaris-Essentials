package io.github.martindavinci.lunaris.scanner.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The licenses screen shows assets/licenses/NOTICES.txt: it must cover what the app bundles. */
class NoticesTest {
    private val notices = File("src/main/assets/licenses/NOTICES.txt").readText()

    @Test fun startsWithTheAppLicense() {
        assertThat(notices).startsWith("Lunaris Scanner is copyright 2026 Martin DaVinci")
        assertThat(notices).contains("GNU General Public License, version 3 or later")
    }

    @Test fun namesTheDirectDependencies() {
        listOf("ZXing core", "CameraX", "Compose", "Dagger and Hilt", "Kotlin standard library", "kotlinx.coroutines")
            .forEach { assertThat(notices).contains(it) }
    }

    @Test fun includesEveryLicenseText() {
        listOf(
            "Apache License\n                           Version 2.0",
            "limitations under the License.",
            "Copyright 2011 The LibYuv Project Authors. All rights reserved.",
        ).forEach { assertThat(notices).contains(it) }
    }
}
