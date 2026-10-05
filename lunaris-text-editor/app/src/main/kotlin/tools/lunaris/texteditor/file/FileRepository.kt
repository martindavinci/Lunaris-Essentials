package tools.lunaris.texteditor.file

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.document.LineEnding
import java.nio.charset.Charset
import javax.inject.Inject

class FileRepository(
    private val gateway: FileGateway,
    private val codec: EncodingCodec,
    /** Runs decode/encode; injectable so tests can keep it on their scheduler. */
    private val cpu: CoroutineDispatcher,
) {
    @Inject constructor(gateway: FileGateway, codec: EncodingCodec) : this(gateway, codec, Dispatchers.Default)

    suspend fun load(ref: FileRef, forced: Charset? = null, maxBytes: Long = Long.MAX_VALUE): LoadedDocument {
        val bytes = gateway.readBytes(ref, maxBytes)
        // Decoding/encoding a multi-MB file is CPU work: keep it off the caller's (main) thread.
        val decoded = withContext(cpu) { codec.decode(bytes, forced) }
        return LoadedDocument(
            ref = ref,
            name = gateway.displayName(ref),
            text = decoded.text,
            charset = decoded.charset,
            lineEnding = decoded.lineEnding,
            hasBom = decoded.hasBom,
        )
    }

    suspend fun save(ref: FileRef, text: String, charset: Charset, lineEnding: LineEnding, hasBom: Boolean = false) {
        val bytes = withContext(cpu) { codec.encode(text, charset, lineEnding, hasBom) }
        gateway.writeBytes(ref, bytes)
    }

    fun canEncode(text: String, charset: Charset): Boolean = codec.canEncode(text, charset)

    suspend fun displayName(ref: FileRef): String? = gateway.displayName(ref)

    suspend fun size(ref: FileRef): Long? = gateway.size(ref)
}
