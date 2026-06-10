package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow

interface FileFilterStore {
    fun fileFilter(): Flow<Boolean>
    suspend fun setFileFilter(on: Boolean)
}
