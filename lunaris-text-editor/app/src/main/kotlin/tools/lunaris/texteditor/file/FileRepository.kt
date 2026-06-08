package tools.lunaris.texteditor.file

import tools.lunaris.texteditor.document.EncodingCodec
import tools.lunaris.texteditor.document.LineEnding
import java.nio.charset.Charset
import javax.inject.Inject

class FileRepository @Inject constructor(
    private val gateway: FileGateway,
    private val codec: EncodingCodec,
) {
    suspend fun load(ref: FileRef, forced: Charset? = null): LoadedDocument {
        val bytes = gateway.readBytes(ref)
        val decoded = codec.decode(bytes, forced)
        return LoadedDocument(
            ref = ref,
            name = gateway.displayName(ref),
            text = decoded.text,
            charset = decoded.charset,
            lineEnding = decoded.lineEnding,
        )
    }

    suspend fun save(ref: FileRef, text: String, charset: Charset, lineEnding: LineEnding) {
        gateway.writeBytes(ref, codec.encode(text, charset, lineEnding))
    }
}
