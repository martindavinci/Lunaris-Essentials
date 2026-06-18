package tools.lunaris.texteditor.file

/** Raw byte IO for a [FileRef]. Implementations are platform-specific (SAF, etc.). */
interface FileGateway {
    suspend fun readBytes(ref: FileRef): ByteArray
    suspend fun writeBytes(ref: FileRef, bytes: ByteArray)
    /** Best-effort display name for a ref, or null if unknown. */
    suspend fun displayName(ref: FileRef): String?
    /** Byte size of the ref, or null if the source doesn't report it. */
    suspend fun size(ref: FileRef): Long?
}
