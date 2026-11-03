package io.github.martindavinci.lunaris.pdf.tts

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class VoiceCatalogTest {
    private fun group(vararg voices: VoiceInfo, preferred: Locale = Locale.ITALY) =
        VoiceCatalog.group(voices.toList(), preferred)

    @Test fun theLanguageInUse_comesFirst_thenTheOthersByName() {
        val groups = group(
            VoiceInfo("de-de-x-nfh-local", Locale.GERMANY, false),
            VoiceInfo("fr-fr-x-frb-local", Locale.FRANCE, false),
            VoiceInfo("it-it-x-itc-local", Locale.ITALY, false),
        )
        assertThat(groups.map { it.locale.language }).containsExactly("it", "fr", "de").inOrder() // francese, tedesco: named in the language in use
    }

    @Test fun variant_isReadFromTheGoogleId() {
        val option = group(VoiceInfo("it-it-x-itc-local", Locale.ITALY, false)).single().voices.single()
        assertThat(option.variant).isEqualTo("itc")
        assertThat(option.isNetwork).isFalse()
    }

    @Test fun anIdWithoutAVariant_hasNone() {
        assertThat(group(VoiceInfo("it-IT-language", Locale.ITALY, true)).single().voices.single().variant).isNull()
    }

    @Test fun offlineVoices_comeBeforeTheOnesThatNeedAConnection() {
        val voices = group(
            VoiceInfo("it-it-x-ita-network", Locale.ITALY, true),
            VoiceInfo("it-it-x-itc-local", Locale.ITALY, false),
        ).single().voices
        assertThat(voices.map { it.isNetwork }).containsExactly(false, true).inOrder()
    }
}
