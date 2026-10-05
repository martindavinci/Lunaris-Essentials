package tools.lunaris.texteditor.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeSessionStore(initial: PersistedSession = PersistedSession("", emptyList())) : SessionStore {
    val flow = MutableStateFlow(initial)
    var saved: PersistedSession? = null
        private set
    /** When set, reads suspend until it completes — simulates DataStore's async first read. */
    var gate: CompletableDeferred<Unit>? = null
    override fun session(): Flow<PersistedSession> = flow { gate?.await(); emitAll(flow) }
    override suspend fun save(session: PersistedSession) { saved = session; flow.value = session }
    override suspend fun clear() { saved = null; flow.value = PersistedSession("", emptyList()) }
}
