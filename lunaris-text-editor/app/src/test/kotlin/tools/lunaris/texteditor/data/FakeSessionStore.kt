package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(initial: PersistedSession = PersistedSession("", emptyList())) : SessionStore {
    val flow = MutableStateFlow(initial)
    var saved: PersistedSession? = null
        private set
    override fun session(): Flow<PersistedSession> = flow
    override suspend fun save(session: PersistedSession) { saved = session; flow.value = session }
    override suspend fun clear() { saved = null; flow.value = PersistedSession("", emptyList()) }
}
