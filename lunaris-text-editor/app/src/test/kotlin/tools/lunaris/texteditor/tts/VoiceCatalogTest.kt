package tools.lunaris.texteditor.tts

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class VoiceCatalogTest {
    private val voices = listOf(
        VoiceInfo("it-it-x-itc-local", Locale("it", "IT"), isNetwork = false),
        VoiceInfo("it-it-x-itd-local", Locale("it", "IT"), isNetwork = false),
        VoiceInfo("en-us-x-sfg-network", Locale("en", "US"), isNetwork = true),
    )

    @Test fun groupsByLanguage() {
        val groups = VoiceCatalog.group(voices, "offline", "requires connection")
        assertThat(groups).hasSize(2)
        val counts = groups.associate { it.language to it.voices.size }
        assertThat(counts.values).containsExactly(2, 1)
    }

    @Test fun labelsIncludeVariantAndConnectivity() {
        val groups = VoiceCatalog.group(voices, "offline", "requires connection")
        val allLabels = groups.flatMap { it.voices }.map { it.label }
        assertThat(allLabels.any { it.contains("itc") && it.contains("offline") }).isTrue()
        assertThat(allLabels.any { it.contains("sfg") && it.contains("requires connection") }).isTrue()
    }

    @Test fun networkFlagPropagated() {
        val opt = VoiceCatalog.group(voices, "offline", "net").flatMap { it.voices }.first { it.id.endsWith("network") }
        assertThat(opt.isNetwork).isTrue()
    }
}
