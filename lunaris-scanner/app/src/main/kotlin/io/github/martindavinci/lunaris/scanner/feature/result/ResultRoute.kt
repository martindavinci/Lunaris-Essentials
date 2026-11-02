package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Shows the code in CurrentResult. Nothing opens by itself: every action waits for a tap. */
@Composable
fun ResultRoute(onBack: () -> Unit) {
    val viewModel: ResultViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leave by rememberUpdatedState(onBack)
    if (state.code == null) {
        LaunchedEffect(Unit) { leave() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val runner = remember(context, launcher) { ResultActionRunner(context) { launcher.launch(it) } }

    ResultScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onUiAction = viewModel::onAction,
        onRun = { action ->
            runner.run(action)?.let { message ->
                scope.launch { snackbar.showSnackbar(context.getString(message)) }
            }
        },
    )
}
