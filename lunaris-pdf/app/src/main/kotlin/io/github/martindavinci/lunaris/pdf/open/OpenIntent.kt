package io.github.martindavinci.lunaris.pdf.open

import android.content.Intent

/** Which document, if any, an incoming intent asks to open. Any app can send anything here. */
object OpenIntent {
    private const val CONTENT = "content:"
    private const val FILE = "file:"
    private const val IMAGE = "image/"

    /** `file:` only from VIEW: a share could otherwise point this app at its own private files. */
    fun uriFrom(action: String?, type: String?, data: String?, stream: String?): String? = when (action) {
        Intent.ACTION_VIEW -> data?.takeIf { it.startsWith(CONTENT) || it.startsWith(FILE) }
        Intent.ACTION_SEND -> stream?.takeIf { it.startsWith(CONTENT) && type != null && !type.startsWith(IMAGE) }
        else -> null
    }

    /** Files for the Create PDF list: several of them, or one image. One PDF is there to be read. */
    fun filesFrom(action: String?, type: String?, streams: List<String>): List<String> {
        val many = action == Intent.ACTION_SEND_MULTIPLE
        val oneImage = action == Intent.ACTION_SEND && type?.startsWith(IMAGE) == true
        return if (many || oneImage) streams.filter { it.startsWith(CONTENT) } else emptyList()
    }

    /**
     * A launch intent is acted on once. Not again when the activity is recreated (process restore),
     * and not when relaunched from Recents, which re-delivers the original intent.
     */
    fun isFresh(recreated: Boolean, flags: Int): Boolean =
        !recreated && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
}
