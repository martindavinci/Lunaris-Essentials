package io.github.martindavinci.lunaris.archives.session

import javax.inject.Inject
import javax.inject.Singleton

/** "Remember until I close the app": passwords by archive key, in memory only. */
@Singleton
class PasswordMemory @Inject constructor() {
    private val stored = HashMap<String, CharArray>()

    @Synchronized
    fun put(key: String, password: CharArray) {
        stored.put(key, password.copyOf())?.fill(WIPED)
    }

    @Synchronized
    fun get(key: String): CharArray? = stored[key]?.copyOf()

    @Synchronized
    fun forgetAll() {
        stored.values.forEach { it.fill(WIPED) }
        stored.clear()
    }

    private companion object { val WIPED = Char(0) }
}
