package tools.lunaris.texteditor.tts

import java.util.Locale

/** A device TTS voice reduced to the fields Android actually exposes (no name/gender). */
data class VoiceInfo(val id: String, val locale: Locale, val isNetwork: Boolean)

/** A selectable voice row in the panel. */
data class VoiceOption(val id: String, val label: String, val isNetwork: Boolean)

/** Voices for one language, shown as a labeled section. */
data class VoiceGroup(val language: String, val voices: List<VoiceOption>)
