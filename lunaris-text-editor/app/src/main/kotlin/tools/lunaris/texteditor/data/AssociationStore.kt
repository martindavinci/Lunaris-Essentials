package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

interface AssociationStore {
    fun associations(): Flow<Associations>
    suspend fun put(extension: String, languageId: String)
    suspend fun removeAssociation(extension: String)
}
