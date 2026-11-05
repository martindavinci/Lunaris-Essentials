package io.github.martindavinci.lunaris.scanner.session

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Time that only moves forward, whatever happens to the phone's clock. */
fun interface MonotonicClock {
    fun millis(): Long
}

class SystemMonotonicClock @Inject constructor() : MonotonicClock {
    override fun millis(): Long = SystemClock.elapsedRealtime()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(clock: SystemMonotonicClock): MonotonicClock
}

private const val LONG_AWAY_MILLIS = 5L * 60 * 1000

/** Opening a link or answering a message is coming back soon. After a longer absence, whoever comes back starts anew. */
class AwayTimer(private val clock: MonotonicClock) {
    private var hiddenAt: Long? = null

    fun hidden() {
        hiddenAt = clock.millis()
    }

    /** It was another screen of the app that took over, which is no absence. */
    fun forget() {
        hiddenAt = null
    }

    /** Whether the user stayed away long. Each absence answers once. */
    fun cameBackLate(): Boolean {
        val since = hiddenAt ?: return false
        hiddenAt = null
        return clock.millis() - since >= LONG_AWAY_MILLIS
    }
}
