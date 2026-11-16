package io.github.martindavinci.lunaris.archives.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordMemoryTest {
    private val memory = PasswordMemory()

    @Test fun get_returnsACopy() {
        memory.put("k", "secret".toCharArray())
        memory.get("k")!!.fill('x')
        assertThat(memory.get("k")!!.concatToString()).isEqualTo("secret")
    }
    @Test fun put_keepsACopy() {
        val typed = "secret".toCharArray()
        memory.put("k", typed)
        typed.fill('x')
        assertThat(memory.get("k")!!.concatToString()).isEqualTo("secret")
    }
    @Test fun forgetAll_wipesEverything() {
        memory.put("a", "1".toCharArray()); memory.put("b", "2".toCharArray())
        memory.forgetAll()
        assertThat(memory.get("a")).isNull(); assertThat(memory.get("b")).isNull()
    }
}
