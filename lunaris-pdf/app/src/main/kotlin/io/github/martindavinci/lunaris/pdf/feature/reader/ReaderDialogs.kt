package io.github.martindavinci.lunaris.pdf.feature.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.url.UrlWarning

private val WarningSpacing = 8.dp

/** The password stays in this dialog's memory: not saved, so a rotation or a restart asks again. */
@Composable
internal fun PasswordDialog(prompt: PasswordPrompt, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember(prompt) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reader_password_title)) },
        text = {
            Column {
                if (prompt.wrong) {
                    Text(stringResource(R.string.reader_password_wrong), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(WarningSpacing))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.reader_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(password) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Links never open by themselves. The whole address shows, with what looks wrong about it. */
@Composable
internal fun LinkDialog(link: PendingLink, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_link_title)) },
        text = {
            Column {
                Text(link.shownUrl, style = MaterialTheme.typography.bodyMedium)
                if (!link.canOpen) {
                    Spacer(Modifier.height(WarningSpacing))
                    Text(stringResource(R.string.reader_link_blocked), color = MaterialTheme.colorScheme.error)
                } else {
                    link.warnings.forEach { warning ->
                        Spacer(Modifier.height(WarningSpacing))
                        Text(stringResource(warning.labelRes), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (link.canOpen) {
                TextButton(onClick = {
                    openLink(context, link.url)
                    onDismiss()
                }) { Text(stringResource(R.string.reader_link_open)) }
            }
            TextButton(onClick = {
                copyLink(context, link.url)
                onDismiss()
            }) { Text(stringResource(R.string.reader_link_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun GoToPageDialog(pages: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val go = { text.toIntOrNull()?.let(onGo) ?: Unit }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(MAX_PAGE_DIGITS) },
                singleLine = true,
                label = { Text(stringResource(R.string.reader_go_to_page_range, pages)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { go() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_PAGE_DIGITS = 7

private val UrlWarning.labelRes: Int
    get() = when (this) {
        UrlWarning.NOT_HTTPS -> R.string.reader_warning_not_https
        UrlWarning.PUNYCODE -> R.string.reader_warning_punycode
        UrlWarning.NON_ASCII_HOST -> R.string.reader_warning_non_ascii_host
        UrlWarning.IP_ADDRESS_HOST -> R.string.reader_warning_ip_address_host
        UrlWarning.EMBEDDED_CREDENTIALS -> R.string.reader_warning_embedded_credentials
        UrlWarning.UNUSUAL_PORT -> R.string.reader_warning_unusual_port
    }

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.reader_link_no_app, Toast.LENGTH_LONG).show()
    }
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.reader_link_title), url))
}
