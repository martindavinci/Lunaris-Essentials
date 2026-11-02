package io.github.martindavinci.lunaris.scanner.share

/** The smallest power-of-two sample size that brings both sides down to [maxSide] or less. */
fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (width / sample > maxSide || height / sample > maxSide) sample *= 2
    return sample
}
