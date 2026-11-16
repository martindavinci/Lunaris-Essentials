package io.github.martindavinci.lunaris.archives.testing

/** Runs at once, but counts: proves the work was handed to the background dispatcher. */
class CountingDispatcher : kotlinx.coroutines.CoroutineDispatcher() {
    var used = 0
    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        used++
        block.run()
    }
}
