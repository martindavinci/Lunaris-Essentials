package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFileFilterStore(initial: Boolean = true) : FileFilterStore {
    val flow = MutableStateFlow(initial)
    override fun fileFilter(): Flow<Boolean> = flow
    override suspend fun setFileFilter(on: Boolean) { flow.value = on }
}
