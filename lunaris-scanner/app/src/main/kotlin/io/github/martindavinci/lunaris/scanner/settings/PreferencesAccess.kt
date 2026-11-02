package io.github.martindavinci.lunaris.scanner.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/** The stored values, or none when the file cannot be read: the defaults beat a crash. */
internal fun DataStore<Preferences>.dataOrEmpty(): Flow<Preferences> = data.catch { failure ->
    if (failure is IOException) emit(emptyPreferences()) else throw failure
}

/** A write that fails, on a full disk for example, leaves the old values and the app running. */
internal suspend fun DataStore<Preferences>.editIfPossible(transform: suspend (MutablePreferences) -> Unit) {
    try {
        edit(transform)
    } catch (_: IOException) {
        // The screens read the stored values back, so they keep showing what is really there.
    }
}
