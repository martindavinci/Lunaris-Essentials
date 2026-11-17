package io.github.martindavinci.lunaris.archives.safety

/** Every numeric limit of the app, named once (spec "Limiti"). */
object Limits {
    private const val KIB = 1024L
    private const val MIB = 1024L * KIB
    private const val GIB = 1024L * MIB
    private const val HOUR_MILLIS = 60L * 60L * 1000L

    const val MAX_ENTRIES = 200_000
    const val MAX_PATH_BYTES = 4096
    const val MAX_PATH_DEPTH = 256
    const val MAX_COMPONENT_BYTES = 255
    const val CACHE_MARGIN_BYTES = 256 * MIB
    /** How often a copy of an archive of unknown size asks for the free space again. */
    const val INCOMING_SPACE_CHECK_BYTES = 64L * MIB
    const val MAX_TEMP_COPY_BYTES = 4 * GIB
    const val OVERRUN_SLACK_BYTES = 64 * KIB
    const val RATIO_CHECK_AFTER_BYTES = 100 * MIB
    const val MAX_RATIO = 1000L
    const val TOTAL_EXTRACT_FACTOR = 1.1
    const val TOTAL_EXTRACT_SLACK_BYTES = 64 * MIB
    const val OPEN_DIR_CAP_BYTES = 1 * GIB
    const val TEMP_MAX_AGE_MILLIS = 24 * HOUR_MILLIS
    const val INDEX_CACHE_FILES = 20
    const val INDEX_CACHE_BYTES = 64 * MIB
    const val MAX_NESTING = 4
    const val RAR_MAX_WINDOW_BYTES = 256 * MIB
    const val MEMORY_SHARE_DIVISOR = 4L
    const val KDF_MAX_LOG2 = 24
    const val ENGINE_BIND_TIMEOUT_MILLIS = 10_000L
    const val ENGINE_IDLE_UNBIND_MILLIS = 30_000L
    const val ENGINE_MAX_DEATHS = 2
    const val ENGINE_CANCEL_GRACE_MILLIS = 2_000L
    const val WIRE_CHUNK_BYTES = 64 * 1024
    const val OPENING_BAR_DELAY_MILLIS = 400L
    const val READING_HINT_DELAY_MILLIS = 300L
    const val RECENTS_MAX = 50
    const val INDEXES_IN_MEMORY = 2
    const val SNIFF_BYTES = 32 * 1024
    const val TEXT_SNIFF_BYTES = 8 * 1024
    /**
     * Every name is held about three times (raw bytes, display text, components), and two indexes can be
     * in memory: 24 MiB of names stays well inside a 256 MiB heap and still fits 200 000 names of 120 bytes.
     */
    const val MAX_INDEX_PATH_BYTES = 24L * MIB
    const val INDEX_PROGRESS_EVERY_ENTRIES = 512
}
