package io.github.martindavinci.lunaris.scanner

import android.content.Intent

/**
 * A launch intent is acted on once. Not again when the activity is recreated (rotation, process
 * restore), and not when relaunched from Recents, which re-delivers the original intent.
 */
fun isFreshLaunchIntent(recreated: Boolean, flags: Int): Boolean =
    !recreated && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
