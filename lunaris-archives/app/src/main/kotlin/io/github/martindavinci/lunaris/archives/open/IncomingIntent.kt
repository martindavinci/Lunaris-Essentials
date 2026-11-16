package io.github.martindavinci.lunaris.archives.open

/** What an intent from another app asks for. */
sealed interface Incoming {
    data class OpenArchive(val uri: String) : Incoming
    data class CreateFrom(val uris: List<String>) : Incoming
    data object TextOnly : Incoming
    data object Nothing : Incoming
}

/** Spec "Ingressi": View opens; Share of one archive opens; other shares start a new ZIP. Only content: URIs count. */
object IncomingIntent {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private const val CONTENT_SCHEME = "content:"

    fun classify(action: String?, data: String?, streams: List<String>, streamLooksLikeArchive: (String) -> Boolean): Incoming {
        val content = streams.filter { it.startsWith(CONTENT_SCHEME) }.distinct()
        return when (action) {
            ACTION_VIEW -> data?.takeIf { it.startsWith(CONTENT_SCHEME) }?.let { Incoming.OpenArchive(it) } ?: Incoming.Nothing
            ACTION_SEND, ACTION_SEND_MULTIPLE -> when {
                content.isEmpty() -> Incoming.TextOnly
                action == ACTION_SEND && content.size == 1 && streamLooksLikeArchive(content.single()) ->
                    Incoming.OpenArchive(content.single())
                else -> Incoming.CreateFrom(content)
            }
            else -> Incoming.Nothing
        }
    }
}
