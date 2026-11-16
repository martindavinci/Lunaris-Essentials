package io.github.martindavinci.lunaris.archives.engine

/** How a job ended; mirrored in native/jni/engine_status.h (EngineStatusHeaderTest compares them). */
enum class EngineStatus(val code: Int) {
    OK(0), NOT_ARCHIVE(1), DAMAGED(2), TRUNCATED(3), HEADERS_ENCRYPTED(4), DATA_ENCRYPTED(5), WRONG_PASSWORD(6),
    RAR4_ENCRYPTED(7), DICTIONARY_TOO_LARGE(8), NEEDS_NEXT_VOLUME(9), UNSUPPORTED_FEATURE(10), CANCELLED(11),
    LIMIT_REACHED(12), OUT_OF_MEMORY(13), ACCESS_DENIED(14), PATH_MISMATCH(15);

    companion object {
        fun of(code: Int): EngineStatus = entries.first { it.code == code }
    }
}

/** How one extracted entry ended. */
enum class EntryOutcome(val code: Int) {
    OK(0), WRONG_PASSWORD(1), DAMAGED(2), UNSUPPORTED(3), LIMIT(4), ENCRYPTED(5), CANCELLED(6);

    companion object {
        fun of(code: Int): EntryOutcome = entries.first { it.code == code }
    }
}
