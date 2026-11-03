package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

private val BoxSpacing = 12.dp
private const val MAX_PAGE_DIGITS_IN_DIALOG = 7

/** The password stays in this dialog's memory: not saved, so a rotation or a restart asks again. */
@Composable
internal fun PasswordDialog(prompt: PasswordPrompt, name: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember(prompt) { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reader_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BoxSpacing)) {
                Text(stringResource(R.string.reader_password_message, name))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = prompt.wrong,
                    label = { Text(stringResource(R.string.reader_password_label)) },
                    supportingText = if (prompt.wrong) {
                        { Text(stringResource(R.string.reader_password_wrong)) }
                    } else {
                        null
                    },
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { shown = !shown }) {
                            Icon(
                                imageVector = if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (shown) R.string.reader_password_hide else R.string.reader_password_show,
                                ),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(password) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.reader_link_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun GoToPageDialog(pages: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var outOfRange by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val go = {
        val page = text.toIntOrNull()
        if (page != null && page in 1..pages) onGo(page) else outOfRange = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter { it.isDigit() }.take(MAX_PAGE_DIGITS_IN_DIALOG)
                    outOfRange = false
                },
                singleLine = true,
                isError = outOfRange,
                suffix = { Text(stringResource(R.string.reader_of_pages, pages)) },
                supportingText = if (outOfRange) {
                    { Text(stringResource(R.string.reader_go_to_page_range, pages)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { go() }) { Text(stringResource(R.string.reader_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
