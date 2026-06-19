package tools.lunaris.texteditor.file

class FakeFileGateway : FileGateway {
    val store = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    /** Optional reported size override (so tests can fake huge files without allocating them). */
    val sizes = mutableMapOf<String, Long>()
    /** Uris that simulate an expired/missing SAF grant (readBytes throws SecurityException). */
    val denied = mutableSetOf<String>()
    /** Uris that simulate a deleted/moved file (readBytes throws FileNotFoundException). */
    val gone = mutableSetOf<String>()

    override suspend fun readBytes(ref: FileRef, maxBytes: Long): ByteArray {
        if (ref.uri in denied) throw SecurityException("no grant for ${ref.uri}")
        if (ref.uri in gone) throw java.io.FileNotFoundException("missing ${ref.uri}")
        val bytes = store[ref.uri] ?: error("no file at ${ref.uri}")
        if (bytes.size.toLong() > maxBytes) throw FileTooLargeException(maxBytes)
        return bytes
    }

    override suspend fun writeBytes(ref: FileRef, bytes: ByteArray) {
        store[ref.uri] = bytes
    }

    override suspend fun displayName(ref: FileRef): String? = names[ref.uri]

    override suspend fun size(ref: FileRef): Long? = sizes[ref.uri] ?: store[ref.uri]?.size?.toLong()
}
