package io.github.martindavinci.lunaris.scanner.share

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/** Files a code read from a shared image in the scan history. */
fun interface ScanRecorder {
    suspend fun record(code: ScannedCode)
}
