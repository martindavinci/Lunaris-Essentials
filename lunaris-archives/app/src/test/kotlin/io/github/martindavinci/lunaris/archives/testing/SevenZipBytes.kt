package io.github.martindavinci.lunaris.archives.testing

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Hand-made 7z files for the key derivation guard. */
internal object SevenZipBytes {
    /** Minimal 7z: signature header pointing at an encoded header whose single folder has one AES coder. */
    fun withAes(cyclesPower: Int, headerBytes: ByteArray? = null): File {
        val header = headerBytes ?: byteArrayOf(
            0x17,                               // kEncodedHeader
            0x06, 0x00, 0x01, 0x09, 0x10, 0x00, // PackInfo: packPos 0, 1 stream, kSize 16, kEnd
            0x07, 0x0B, 0x01, 0x00,             // UnpackInfo, kFolder, 1 folder, not external
            0x01,                               // 1 coder
            0x24,                               // idSize 4, has attributes
            0x06, 0xF1.toByte(), 0x07, 0x01,    // AES-256 + SHA-256 coder id
            0x02, cyclesPower.toByte(), 0x00,   // props size 2: first byte = numCyclesPower, no salt/iv
            0x0C, 0x10, 0x00, 0x00)             // kCodersUnpackSize 16, kEnd, kEnd
        val sig = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4)); putInt(0)
            putLong(0); putLong(header.size.toLong()); putInt(0)
        }.array()
        return File.createTempFile("kdf", ".7z").apply { writeBytes(sig + header); deleteOnExit() }
    }
}
