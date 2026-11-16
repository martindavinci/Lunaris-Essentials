package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.ui.theme.accentText

private val FieldShape = RoundedCornerShape(12.dp)

/**
 * A password field over a [TextFieldState] the caller holds with `remember` (never
 * `rememberSaveable`: saved state can reach the disk).
 *
 * In Material3 1.4.0 `OutlinedSecureTextField` and the `TextFieldState` overloads carry no
 * experimental annotation: no cut, copy or drag out of the field, no autocorrect, and the text
 * never lives in a String held by the state of a screen.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.accentText
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    imageVector = if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (shown) R.string.password_hide else R.string.password_show),
                )
            }
        },
        supportingText = supporting?.let { text -> { Text(text) } },
        isError = isError,
        textObfuscationMode = if (shown) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        onKeyboardAction = { onIme() },
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = accent),
    )
}

/** The characters of the field as an array the caller wipes after use. No String is made on the way. */
fun TextFieldState.toCharArray(): CharArray {
    val current = text
    return CharArray(current.length) { current[it] }
}
