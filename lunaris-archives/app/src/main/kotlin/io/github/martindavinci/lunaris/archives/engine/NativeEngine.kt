package io.github.martindavinci.lunaris.archives.engine

// Loaded only in the isolated :engine process (B3). probe() proves the native build links; T3.2 replaces it.
internal object NativeEngine {
    init { System.loadLibrary("lunaris_engine") }
    @JvmStatic external fun probe(): String
}
