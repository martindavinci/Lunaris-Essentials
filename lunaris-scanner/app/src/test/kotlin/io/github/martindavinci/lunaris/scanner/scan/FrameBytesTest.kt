package io.github.martindavinci.lunaris.scanner.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer

class FrameBytesTest {
    @Test fun copyLuminance_takesARegionOfTheFrame() {
        val stride = 5
        val plane = java.nio.ByteBuffer.wrap(ByteArray(stride * 3) { it.toByte() })

        val out = copyLuminance(plane, stride, width = 2, height = 2, left = 1, top = 1)

        assertThat(out.toList()).containsExactly(6.toByte(), 7.toByte(), 11.toByte(), 12.toByte()).inOrder()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test fun rowsWithoutPadding_areCopiedAsTheyAre() {
        val plane = ByteBuffer.wrap(bytes(1, 2, 3, 4, 5, 6))

        assertThat(copyLuminance(plane, rowStride = 2, width = 2, height = 3)).isEqualTo(bytes(1, 2, 3, 4, 5, 6))
    }

    @Test fun rowPadding_isSkipped() {
        val plane = ByteBuffer.wrap(bytes(1, 2, 3, 9, 9, 4, 5, 6, 9, 9))

        assertThat(copyLuminance(plane, rowStride = 5, width = 3, height = 2)).isEqualTo(bytes(1, 2, 3, 4, 5, 6))
    }

    @Test fun lastRowWithoutItsPadding_isEnough() {
        val plane = ByteBuffer.wrap(bytes(1, 2, 9, 3, 4))

        assertThat(copyLuminance(plane, rowStride = 3, width = 2, height = 2)).isEqualTo(bytes(1, 2, 3, 4))
    }

    @Test fun readingLeavesTheBufferUntouched() {
        val plane = ByteBuffer.wrap(bytes(1, 2, 3, 4))

        copyLuminance(plane, rowStride = 2, width = 2, height = 2)

        assertThat(plane.position()).isEqualTo(0)
    }

    @Test fun rotation_turnsClockwise() {
        // 1 2 3      4 1
        // 4 5 6  ->  5 2
        //            6 3
        val rotated = rotate90(bytes(1, 2, 3, 4, 5, 6), width = 3, height = 2)

        assertThat(rotated).isEqualTo(bytes(4, 1, 5, 2, 6, 3))
    }

    @Test fun opaquePixels_useTheWeightedAverage() {
        val pixels = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00FF00.toInt(), 0xFFFF0000.toInt())

        assertThat(argbToLuminance(pixels, count = 4)).isEqualTo(bytes(255, 0, 127, 63))
    }

    @Test fun transparency_blendsTowardsWhite() {
        val pixels = intArrayOf(0x00000000, 0x80000000.toInt())

        assertThat(argbToLuminance(pixels, count = 2)).isEqualTo(bytes(255, 127))
    }

    @Test fun onlyTheAskedPixels_areConverted() {
        assertThat(argbToLuminance(IntArray(4), count = 2)).hasLength(2)
    }
}
