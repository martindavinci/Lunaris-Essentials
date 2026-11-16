package io.github.martindavinci.lunaris.archives.engine

/** The JNI surface of libarchive; loaded only in the isolated :engine process. */
internal object NativeEngine {
    init { System.loadLibrary("lunaris_engine") }

    @JvmStatic external fun newJob(): Long
    @JvmStatic external fun cancel(job: Long)
    @JvmStatic external fun freeJob(job: Long)
    @JvmStatic external fun list(job: Long, fds: IntArray, maxEntries: Int, sink: NativeSink): Int
    @JvmStatic external fun extract(job: Long, fds: IntArray, indices: LongArray, sink: NativeSink): Int
}
