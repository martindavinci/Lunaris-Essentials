package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.ui.AccentTextButton
import io.github.martindavinci.lunaris.archives.ui.CheckRow
import io.github.martindavinci.lunaris.archives.ui.DeepButton
import io.github.martindavinci.lunaris.archives.ui.PasswordField
import io.github.martindavinci.lunaris.archives.ui.theme.accentText
import io.github.martindavinci.lunaris.archives.ui.toCharArray
import kotlinx.coroutines.flow.drop

private val BodyToField = 24.dp
private val FieldToCheck = 4.dp
private val SpinnerSize = 24.dp
private val SpinnerStroke = 2.dp
private const val HIDDEN = 0f
private const val SHOWN = 1f

/**
 * Frames "08 Password prompt" and "08 Password prompt wrong", plus the undrawn "Checking…".
 *
 * The text lives in a [TextFieldState] held with `remember`: not saved, not in the screen's
 * state. It stays in the field until the dialog goes away (the "wrong" frame shows it, selected,
 * to be typed over) and is cleared then. The array given to [onSubmit] is the caller's to wipe.
 */
@Composable
internal fun PasswordDialog(prompt: PasswordPrompt, onSubmit: (CharArray, Boolean) -> Unit, onEdited: () -> Unit, onDismiss: () -> Unit) {
    val field = remember { TextFieldState() }
    var keep by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) { onDispose { field.clearText() } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(prompt.wrong) { if (prompt.wrong) field.edit { selectAll() } }
    // Typing takes the "wrong password" mark away. The length is watched, never the text.
    LaunchedEffect(field) { snapshotFlow { field.text.length }.drop(1).collect { onEdited() } }

    val canSubmit = field.text.isNotEmpty() && !prompt.checking
    val submit = { if (canSubmit) onSubmit(field.toCharArray(), keep) }
    val supporting = when {
        prompt.checking -> stringResource(R.string.password_checking)
        prompt.wrong -> stringResource(R.string.password_wrong)
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (!prompt.checking) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.accentText,
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.password_body, prompt.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(BodyToField))
                PasswordField(
                    state = field,
                    label = stringResource(R.string.password_label),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    isError = prompt.wrong && !prompt.checking,
                    supporting = supporting,
                    enabled = !prompt.checking,
                    imeAction = ImeAction.Done,
                    onIme = submit,
                )
                Spacer(Modifier.height(FieldToCheck))
                CheckRow(stringResource(R.string.password_remember), keep, onChange = { keep = it }, enabled = !prompt.checking)
            }
        },
        dismissButton = { AccentTextButton(stringResource(R.string.cancel), onDismiss, enabled = !prompt.checking) },
        confirmButton = {
            // While checking, the wheel takes the place of the button without the dialog changing size.
            Box(contentAlignment = Alignment.Center) {
                DeepButton(stringResource(R.string.open), submit, modifier = Modifier.alpha(if (prompt.checking) HIDDEN else SHOWN), enabled = canSubmit)
                if (prompt.checking) CircularProgressIndicator(Modifier.size(SpinnerSize), strokeWidth = SpinnerStroke)
            }
        },
    )
}
