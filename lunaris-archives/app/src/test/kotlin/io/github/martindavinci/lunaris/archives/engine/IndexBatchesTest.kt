package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexBatchesTest {
    @Test fun aFewIndices_goInOneCall() {
        val indices = longArrayOf(9, 3, 5)
        assertThat(IndexBatches.of(indices).single()).isSameInstanceAs(indices)
    }
    @Test fun manyIndices_goInSortedBatchesUnderTheBinderLimit() {
        val indices = LongArray(200_000) { 199_999L - it }
        val batches = IndexBatches.of(indices)
        assertThat(batches).hasSize(7)
        assertThat(batches.maxOf { it.size }).isAtMost(IndexBatches.MAX_PER_CALL)
        assertThat(batches.flatMap { it.toList() }).isEqualTo((0L until 200_000L).toList())
    }
}
