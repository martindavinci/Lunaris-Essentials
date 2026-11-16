package io.github.martindavinci.lunaris.archives.open

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Reading an intent from another app, which can send anything. */
object OpenIntent {
    /**
     * A launch intent is acted on once. Not again when the activity is recreated (process restore),
     * and not when relaunched from Recents, which re-delivers the original intent.
     */
    fun isFresh(recreated: Boolean, flags: Int): Boolean =
        !recreated && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    /** The shared streams as strings; empty when the extra is missing, of another type, or cannot be unpacked. */
    @Suppress("DEPRECATION")
    fun streamsOf(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        return try {
            val many = when {
                intent.action != Intent.ACTION_SEND_MULTIPLE -> null
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            (many?.toList() ?: listOfNotNull(streamOf(intent))).map(Uri::toString)
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    } catch (_: RuntimeException) {
        null
    }
}
