package tools.lunaris.texteditor.file

class FakeFileGateway : FileGateway {
    val store = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    /** Optional reported size override (so tests can fake huge files without allocating them). */
    val sizes = mutableMapOf<String, Long>()

    override suspend fun readBytes(ref: FileRef): ByteArray =
        store[ref.uri] ?: error("no file at ${ref.uri}")

    override suspend fun writeBytes(ref: FileRef, bytes: ByteArray) {
        store[ref.uri] = bytes
    }

    override suspend fun displayName(ref: FileRef): String? = names[ref.uri]

    override suspend fun size(ref: FileRef): Long? = sizes[ref.uri] ?: store[ref.uri]?.size?.toLong()
}
