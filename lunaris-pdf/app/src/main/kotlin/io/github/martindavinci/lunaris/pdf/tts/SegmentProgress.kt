package io.github.martindavinci.lunaris.pdf.tts

/** Tells when the last chunk of the segment being spoken has ended, well or not. The engine calls from its own thread. */
internal class SegmentProgress(private val onSegmentDone: (String) -> Unit) {
    @Volatile private var segmentId: String? = null
    @Volatile private var lastChunkId: String? = null

    /** The utterance ids of the [chunks] of a new segment. */
    fun begin(segmentId: String, chunks: Int): List<String> {
        val ids = List(chunks) { "$segmentId#$it" }
        this.segmentId = segmentId
        lastChunkId = ids.last()
        return ids
    }

    fun chunkFinished(utteranceId: String?) {
        if (utteranceId != null && utteranceId == lastChunkId) segmentId?.let(onSegmentDone)
    }

    fun chunkFailed(utteranceId: String?) = chunkFinished(utteranceId)

    fun clear() {
        segmentId = null
        lastChunkId = null
    }
}
