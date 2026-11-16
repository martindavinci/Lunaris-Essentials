package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind

/** Turns a libarchive error into a status; first match wins. Re-read the messages in the submodule on every bump. */
object EngineErrors {
    const val LIBARCHIVE_VERSION = "3.8.11"
    private const val ENOMEM = 12
    private const val EACCES = 13
    private const val EPERM = 1
    private val HEADER_ENCRYPTION_KINDS = setOf(ArchiveKind.RAR5, ArchiveKind.SEVEN_Z)
    private val DATA_ENCRYPTED = listOf(
        "Reading encrypted data is not currently supported",
        "Decryption is unsupported due to lack of crypto library",
        "Encrypted file is unsupported",
    )

    fun classify(kind: ArchiveKind, archiveErrno: Int, message: String?): EngineStatus {
        val m = message.orEmpty()
        return when {
            m.startsWith("Unrecognized archive format") -> EngineStatus.NOT_ARCHIVE
            m.startsWith("Encryption is not supported") -> headersEncrypted(kind)
            m.contains("is encrypted, but currently not supported") ->
                if (m.contains("header")) headersEncrypted(kind) else EngineStatus.DATA_ENCRYPTED
            DATA_ENCRYPTED.any { m.startsWith(it) } -> EngineStatus.DATA_ENCRYPTED
            m.startsWith("RAR encryption support unavailable") -> EngineStatus.RAR4_ENCRYPTED
            m.startsWith("Declared dictionary size is not supported") -> EngineStatus.DICTIONARY_TOO_LARGE
            m.startsWith("Truncated RAR5 archive: file continues in a subsequent volume") -> EngineStatus.NEEDS_NEXT_VOLUME
            m.startsWith("Truncated ") -> EngineStatus.TRUNCATED
            archiveErrno == ENOMEM -> EngineStatus.OUT_OF_MEMORY
            archiveErrno == EACCES || archiveErrno == EPERM -> EngineStatus.ACCESS_DENIED
            else -> EngineStatus.DAMAGED
        }
    }

    private fun headersEncrypted(kind: ArchiveKind) =
        if (kind in HEADER_ENCRYPTION_KINDS) EngineStatus.HEADERS_ENCRYPTED else EngineStatus.DAMAGED
}
