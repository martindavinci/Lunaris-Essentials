package io.github.martindavinci.lunaris.pdf.feature.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The licenses screen shows assets/licenses/NOTICES.txt: it must cover what the app bundles. */
class NoticesTest {
    private val notices = File("src/main/assets/licenses/NOTICES.txt").readText()
    private val flatNotices = notices.flattened()

    private fun String.flattened() = replace(Regex("""\s+"""), " ")

    @Test fun startsWithTheAppLicense() {
        assertThat(notices).startsWith("Lunaris PDF is copyright 2026 Martin DaVinci")
        assertThat(notices).contains("GNU General Public License, version 3 or later")
    }

    @Test fun namesTheDirectDependencies() {
        listOf("PDF.js", "Compose", "webkit", "Dagger and Hilt", "Kotlin standard library", "kotlinx.coroutines")
            .forEach { assertThat(notices).contains(it) }
    }

    @Test fun includesTheApacheLicense() {
        assertThat(notices).contains("Apache License\n                           Version 2.0")
        assertThat(notices).contains("limitations under the License.")
    }

    @Test fun includesEveryLicenseShippedWithPdfJs() {
        val folders = listOf("wasm", "standard_fonts", "cmaps", "iccs").map { File("src/main/assets/viewer/$it") }
        val licenses = folders.flatMap { folder -> folder.listFiles { f -> f.name.startsWith("LICENSE") }!!.toList() }
        assertThat(licenses).isNotEmpty()
        licenses.forEach { license ->
            assertThat(flatNotices).contains(license.readText().flattened().trim())
        }
    }

    @Test fun theScriptEngineOfPdfJs_isNotShipped() {
        assertThat(File("src/main/assets/viewer/wasm").list()!!.filter { it.contains("quickjs", ignoreCase = true) }).isEmpty()
    }
}
