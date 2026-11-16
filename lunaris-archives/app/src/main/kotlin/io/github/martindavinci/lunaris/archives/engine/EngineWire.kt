package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.safety.Limits

/**
 * Length-prefixed messages from the engine to the app: a VERSION byte, then tagged records.
 * Field layout: [WireWriter]; bounds: [WireReader].
 */
object EngineWire {
    const val VERSION = 1
    const val CHUNK_BYTES = Limits.WIRE_CHUNK_BYTES
    const val TAG_ENTRY = 1
    const val TAG_ARCHIVE = 2
    const val TAG_DATA_START = 3
    const val TAG_DATA = 4
    const val TAG_PROGRESS = 5
    const val TAG_DATA_END = 6
    const val TAG_STATUS = 7
    internal const val MAX_PATH_BYTES = 65_536
    internal const val MAX_COMMENT_BYTES = 1 shl 20
}
