package io.github.martindavinci.lunaris.archives.engine

/** The reader of each route, inside the engine process. */
object IsolatedReaders {
    fun of(route: EngineRoute): IsolatedReader = when (route) {
        EngineRoute.LIBARCHIVE -> LibarchiveReader()
        EngineRoute.ZIP4J, EngineRoute.SEVEN_Z, EngineRoute.LIB_RAR -> throw UnsupportedOperationException("route $route")
    }
}
