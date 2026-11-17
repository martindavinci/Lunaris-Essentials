package io.github.martindavinci.lunaris.archives.engine

/** A oneway binder call carries about 500 KB, and 200 000 indices are 1.6 MB: they go in several passes. */
internal object IndexBatches {
    const val MAX_PER_CALL = 32_768   // 256 KB of indices

    fun of(indices: LongArray, max: Int = MAX_PER_CALL): List<LongArray> =
        if (indices.size <= max) listOf(indices) else indices.sorted().chunked(max) { it.toLongArray() }
}
