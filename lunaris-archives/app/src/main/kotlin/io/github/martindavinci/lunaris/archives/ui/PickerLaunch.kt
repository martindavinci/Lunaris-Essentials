package io.github.martindavinci.lunaris.archives.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import io.github.martindavinci.lunaris.archives.R

/** Starts a system picker; [onMissing] runs when the device has none (some TV boxes, a disabled Files app). */
fun <I> ActivityResultLauncher<I>.launchOr(input: I, onMissing: () -> Unit) {
    try {
        launch(input)
    } catch (_: ActivityNotFoundException) {
        onMissing()
    }
}

/** For a screen without a snackbar of its own. */
fun Context.sayNoPicker() {
    Toast.makeText(this, R.string.home_no_picker, Toast.LENGTH_LONG).show()
}
