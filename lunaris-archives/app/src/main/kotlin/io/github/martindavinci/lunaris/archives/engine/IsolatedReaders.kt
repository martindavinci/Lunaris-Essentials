package io.github.martindavinci.lunaris.archives.engine

/** The reader of each route, inside the engine process. */
object IsolatedReaders {
    fun of(route: EngineRoute): IsolatedReader = when (route) {
        EngineRoute.LIBARCHIVE -> LibarchiveReader()
        EngineRoute.ZIP4J -> ZipCryptoReader()
        EngineRoute.SEVEN_Z -> SevenZipReader()
        EngineRoute.LIB_RAR -> RarReader5()
    }
}
