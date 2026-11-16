package io.github.martindavinci.lunaris.archives.engine

/** Which reader inside the engine handles an archive. */
enum class EngineRoute(val code: Int) {
    LIBARCHIVE(0), ZIP4J(1), SEVEN_Z(2), LIB_RAR(3);

    companion object {
        fun of(code: Int): EngineRoute = entries.first { it.code == code }
    }
}
