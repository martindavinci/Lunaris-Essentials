package io.github.martindavinci.lunaris.scanner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** A store whose file cannot be read when [readWorks] is false, and that can never be written. */
internal class FailingDataStore(private val readWorks: Boolean) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow {
        if (readWorks) emit(emptyPreferences()) else throw IOException("unreadable")
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("disk full")
}
