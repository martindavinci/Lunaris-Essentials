package io.github.martindavinci.lunaris.scanner.scan

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

private val WEAKLY_CHECKED_FORMATS = setOf(
    CodeFormat.EAN_13,
    CodeFormat.EAN_8,
    CodeFormat.UPC_A,
    CodeFormat.UPC_E,
    CodeFormat.CODE_39,
    CodeFormat.CODABAR,
    CodeFormat.ITF,
)

/** One check digit or none: a stretch of bars read wrongly can still pass for one of these. */
internal val CodeFormat.isWeaklyChecked: Boolean get() = this in WEAKLY_CHECKED_FORMATS

/**
 * A misread of a weakly checked barcode, such as half of a longer one, gives other digits in the
 * next frame; a real one gives the same. Such a code counts only once two reads in a row agree.
 */
internal class ReadAgreement(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private var candidate: ScannedCode? = null
    private var candidateMillis = 0L

    @Synchronized
    fun accept(code: ScannedCode, nowMillis: Long): Boolean {
        if (!code.format.isWeaklyChecked) return true
        val agrees = code == candidate && nowMillis - candidateMillis <= windowMillis
        candidate = if (agrees) null else code
        candidateMillis = nowMillis
        return agrees
    }

    @Synchronized
    fun reset() {
        candidate = null
    }

    private companion object {
        const val DEFAULT_WINDOW_MILLIS = 2_000L
    }
}
