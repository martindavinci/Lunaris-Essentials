package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAssociationStore(initial: Associations = Associations.empty()) : AssociationStore {
    val flow = MutableStateFlow(initial)
    override fun associations(): Flow<Associations> = flow
    override suspend fun put(extension: String, languageId: String) { flow.value = flow.value.put(extension, languageId) }
    override suspend fun remove(extension: String) { flow.value = flow.value.remove(extension) }
}
