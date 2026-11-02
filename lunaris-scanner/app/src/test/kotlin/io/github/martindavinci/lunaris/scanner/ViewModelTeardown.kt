package io.github.martindavinci.lunaris.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain

/**
 * Stops the view models and the scope of a real DataStore, then resets Dispatchers.Main. In this
 * order: work still running on other threads would otherwise resume on the reset dispatcher, and
 * the exception would fail whichever test runs next.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun stopCoroutinesAndResetMain(viewModels: List<ViewModel>, storeScope: CoroutineScope) {
    runBlocking {
        viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        storeScope.coroutineContext.job.cancelAndJoin()
    }
    Dispatchers.resetMain()
}
