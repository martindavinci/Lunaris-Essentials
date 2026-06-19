package tools.lunaris.texteditor.file

import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.document.LineEnding
import java.nio.charset.Charset
import javax.inject.Inject

class FileRepository @Inject constructor(
    private val gateway: FileGateway,
    private val codec: EncodingCodec,
) {
    suspend fun load(ref: FileRef, forced: Charset? = null, maxBytes: Long = Long.MAX_VALUE): LoadedDocument {
        val bytes = gateway.readBytes(ref, maxBytes)
        val decoded = codec.decode(bytes, forced)
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
        gateway.writeBytes(ref, codec.encode(text, charset, lineEnding, hasBom))
    }

    suspend fun displayName(ref: FileRef): String? = gateway.displayName(ref)

    suspend fun size(ref: FileRef): Long? = gateway.size(ref)
}
