package io.github.martindavinci.lunaris.archives.feature.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** NOTICES.txt names every bundled part and carries the licence files of the native sources as they ship. */
class NoticesTest {
    private val notices = File("src/main/assets/licenses/NOTICES.txt").readText()
    private fun flat(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    @Test fun startsWithOurCopyrightAndGpl() {
        assertThat(notices).startsWith("Lunaris Archives is copyright 2026 Martin DaVinci")
        assertThat(flat(notices)).contains("GNU General Public License, version 3 or later")
    }

    @Test fun namesEveryComponent() {
        listOf(
            "libarchive", "bzip2", "XZ Utils", "liblzma", "Zstandard", "LZ4", "zlib", "zip4j", "Apache Commons Compress",
            "Apache Commons IO", "Apache Commons Lang", "Apache Commons Codec", "XZ for Java", "lib-rar", "Grzegorz Antoniak",
            "Samuel Neves", "Igor Pavlov", "AndroidX", "Dagger", "Kotlin",
        ).forEach { assertThat(notices).contains(it) }
    }

    @Test fun submoduleLicencesAreCopiedVerbatim() {
        listOf("../native/libarchive/COPYING", "../native/bzip2/LICENSE", "../native/zstd/LICENSE", "../native/lz4/lib/LICENSE", "../native/xz/COPYING.0BSD")
            .forEach { assertThat(flat(notices)).contains(flat(File(it).readText())) }
    }

    @Test fun fullLicenceTextsArePresent() {
        listOf("Apache License\n                           Version 2.0", "GNU LESSER GENERAL PUBLIC LICENSE\n                       Version 2.1")
            .forEach { assertThat(flat(notices)).contains(flat(it)) }
        assertThat(notices).contains("limitations under the License.")
    }

    @Test fun saysTheApkIsGplVersion3BecauseOfLibRar() {
        assertThat(flat(notices)).contains("GNU GPL version 3")
    }
}
