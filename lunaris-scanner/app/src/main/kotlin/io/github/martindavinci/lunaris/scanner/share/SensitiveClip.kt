package io.github.martindavinci.lunaris.scanner.share

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/** From Android 13 this keeps the text out of the clipboard preview the system shows. */
internal fun ClipData.markSensitive() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
}
