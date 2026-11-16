package io.github.martindavinci.lunaris.archives.archive

import java.util.Locale

/** Format from the first bytes; the file name only when the bytes say nothing (spec "Riconoscimento"). */
object ArchiveSignatures {
    const val HEAD_BYTES = 32 * 1024
    const val ISO_PROBE_END = 0x8006
    private const val TAR_MAGIC_OFFSET = 257
    private const val ISO_MAGIC_OFFSET = 32769

    private val SIGNATURES: List<Pair<ArchiveKind, ByteArray>> = listOf(
        ArchiveKind.RAR5 to bytes(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00),
        ArchiveKind.RAR4 to bytes(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00),
        ArchiveKind.ZIP to bytes(0x50, 0x4B, 0x03, 0x04),
        ArchiveKind.ZIP to bytes(0x50, 0x4B, 0x05, 0x06),
        ArchiveKind.ZIP_SPLIT to bytes(0x50, 0x4B, 0x07, 0x08),
        ArchiveKind.SEVEN_Z to bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C),
        ArchiveKind.XZ to bytes(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00),
        ArchiveKind.GZIP to bytes(0x1F, 0x8B),
        ArchiveKind.BZIP2 to bytes(0x42, 0x5A, 0x68),
        ArchiveKind.ZSTD to bytes(0x28, 0xB5, 0x2F, 0xFD),
        ArchiveKind.LZ4 to bytes(0x04, 0x22, 0x4D, 0x18),
        ArchiveKind.CPIO to "070701".toByteArray(), ArchiveKind.CPIO to "070702".toByteArray(),
        ArchiveKind.CPIO to "070707".toByteArray(), ArchiveKind.CPIO to bytes(0xC7, 0x71),
        ArchiveKind.CPIO to bytes(0x71, 0xC7),
    )
    private val EXTENSIONS = mapOf(
        "zip" to ArchiveKind.ZIP, "7z" to ArchiveKind.SEVEN_Z, "rar" to ArchiveKind.RAR5, "tar" to ArchiveKind.TAR,
        "gz" to ArchiveKind.GZIP, "tgz" to ArchiveKind.GZIP, "bz2" to ArchiveKind.BZIP2, "tbz2" to ArchiveKind.BZIP2,
        "xz" to ArchiveKind.XZ, "txz" to ArchiveKind.XZ, "zst" to ArchiveKind.ZSTD, "tzst" to ArchiveKind.ZSTD,
        "lz4" to ArchiveKind.LZ4, "iso" to ArchiveKind.ISO9660, "cpio" to ArchiveKind.CPIO,
    )
    private val FILTERS = setOf(ArchiveKind.GZIP, ArchiveKind.BZIP2, ArchiveKind.XZ, ArchiveKind.ZSTD, ArchiveKind.LZ4)

    fun detect(head: ByteArray, displayName: String?): ArchiveKind {
        SIGNATURES.firstOrNull { (_, sig) -> head.startsWith(sig, 0) }?.let { return it.first }
        if (head.startsWith("ustar".toByteArray(), TAR_MAGIC_OFFSET)) return ArchiveKind.TAR
        if (head.startsWith("CD001".toByteArray(), ISO_MAGIC_OFFSET)) return ArchiveKind.ISO9660
        val ext = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return EXTENSIONS[ext] ?: ArchiveKind.UNKNOWN
    }

    fun formatLabel(kind: ArchiveKind, innerTar: Boolean): String =
        if (innerTar && kind in FILTERS) "TAR.${kind.label}" else kind.label

    private fun ByteArray.startsWith(sig: ByteArray, offset: Int): Boolean =
        size >= offset + sig.size && sig.indices.all { this[offset + it] == sig[it] }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
}
