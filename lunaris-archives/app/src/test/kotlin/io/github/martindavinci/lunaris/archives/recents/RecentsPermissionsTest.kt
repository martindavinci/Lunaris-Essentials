package io.github.martindavinci.lunaris.archives.recents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentsPermissionsTest {
    private val taken = mutableListOf<Pair<String, Int>>()
    private val released = mutableListOf<Pair<String, Int>>()
    private var writeAllowed = true
    private val calls = object : PermissionCalls {
        override fun take(uri: String, flags: Int): Boolean {
            if (flags and WRITE != 0 && !writeAllowed) return false
            taken += uri to flags
            return true
        }
        override fun release(uri: String, flags: Int) { released += uri to flags }
    }
    private val permissions = ContentUriPermissions(calls)

    @Test fun take_keepsWriteToo_whenTheProviderAllows() {
        assertThat(permissions.takeRead("content://a")).isTrue()
        assertThat(taken).containsExactly("content://a" to (READ or WRITE))
    }
    @Test fun take_fallsBackToReadOnly() {
        writeAllowed = false
        assertThat(permissions.takeRead("content://a")).isTrue()
        assertThat(taken).containsExactly("content://a" to READ)
    }
    @Test fun release_releasesReadAndWrite() {
        permissions.release("content://a")
        assertThat(released).containsExactly("content://a" to (READ or WRITE), "content://a" to READ).inOrder()
    }

    private companion object {
        const val READ = 1
        const val WRITE = 2
    }
}
