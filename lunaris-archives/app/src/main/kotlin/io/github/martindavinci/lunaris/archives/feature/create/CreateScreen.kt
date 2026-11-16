package io.github.martindavinci.lunaris.archives.feature.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.create.CreateState
import io.github.martindavinci.lunaris.archives.create.PasswordStrength
import io.github.martindavinci.lunaris.archives.feature.extract.KeepScreenOn
import io.github.martindavinci.lunaris.archives.ui.SimpleTopBar
import io.github.martindavinci.lunaris.archives.ui.toCharArray

/**
 * Frames 13, 14 and 15. [onCreate] is given the password (or null) when "Create ZIP" is pressed:
 * the route keeps it while the system's "save as" is open and puts it in [CreateAction.CreateAt].
 */
@Composable
internal fun CreateScreen(
    state: CreateUiState,
    onAction: (CreateAction) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onCreate: (CharArray?) -> Unit,
    onClose: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    val barColor by animateColorAsState(if (scrolled) colors.surface else colors.background, label = "bar")
    val password = remember { TextFieldState() }
    val confirm = remember { TextFieldState() }
    DisposableEffect(Unit) { onDispose { password.clearText(); confirm.clearText() } }
    LaunchedEffect(password, confirm) {
        snapshotFlow { password.text to confirm.text }.collect { (first, second) ->
            val chars = CharArray(first.length) { first[it] }
            val level = PasswordStrength.level(chars)
            chars.fill(Char(0))
            val same = first.length == second.length && first.indices.all { first[it] == second[it] }
            onAction(CreateAction.PasswordChanged(level, same))
        }
    }
    val phase = state.state
    val running = phase is CreateState.Running
    BackHandler { if (running) onAction(CreateAction.RequestStop) else onClose() }
    KeepScreenOn(running)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().background(barColor).systemBarsPadding()) {
            SimpleTopBar(
                title = stringResource(R.string.create_title),
                navIcon = Icons.Rounded.Close,
                navLabel = stringResource(R.string.close),
                onNav = { if (running) onAction(CreateAction.RequestStop) else onClose() },
                background = barColor,
            )
            Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                when (phase) {
                    is CreateState.Running -> CreatingCard(phase, state.name, onAction)
                    is CreateState.Finished -> CreatedCard(phase, onAction)
                    else -> {
                        CreateContent(state, onAction, onPickFiles, onPickFolder)
                        if (state.items.isNotEmpty()) {
                            CreateOptions(state, password, confirm, onAction, onCreate = { onCreate(if (state.protect) password.toCharArray() else null) })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
    }
    if (state.confirmStop && running) {
        CreateStopDialog(onKeepGoing = { onAction(CreateAction.KeepGoing) }, onStop = { onAction(CreateAction.Stop) })
    }
}
