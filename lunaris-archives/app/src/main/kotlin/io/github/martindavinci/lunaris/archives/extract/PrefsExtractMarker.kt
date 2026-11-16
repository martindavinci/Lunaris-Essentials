package io.github.martindavinci.lunaris.archives.extract

import javax.inject.Inject
import javax.inject.Singleton

/** One stored string (SharedPreferences on the device). */
interface KeyValue {
    fun get(): String?
    fun put(value: String)
    fun remove()
}

/** The document being written, kept where a restart after process death can find it. */
@Singleton
class PrefsExtractMarker @Inject constructor(private val store: KeyValue) : ExtractMarker {
    @Volatile private var recovered = false

    override fun set(token: String) = store.put(token)

    override fun clear() = store.remove()

    fun pending(): String? = store.get()

    /** Deletes the half-written document a dead process left; true when there was one. */
    fun recover(delete: (String) -> Boolean): Boolean {
        val token = pending() ?: return false
        runCatching { delete(token) }
        clear()
        recovered = true
        return true
    }

    /** True once after a recovery, for the message on the next screen. */
    fun takeRecovered(): Boolean = recovered.also { recovered = false }
}
