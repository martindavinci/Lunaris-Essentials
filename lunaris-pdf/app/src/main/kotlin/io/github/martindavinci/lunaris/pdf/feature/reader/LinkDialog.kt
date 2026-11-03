package io.github.martindavinci.lunaris.pdf.feature.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.url.UrlWarning

private val BoxShape = RoundedCornerShape(16.dp)
private val BoxPadding = 14.dp
private val BoxSpacing = 12.dp
private val LineSpacing = 8.dp
private val WarningIconSize = 20.dp

/** Links never open by themselves. The whole address shows, with what looks wrong about it. */
@Composable
internal fun LinkDialog(link: PendingLink, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val risky = link.warnings.isNotEmpty() || !link.canOpen
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Link, contentDescription = null) },
        title = { Text(stringResource(R.string.reader_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BoxSpacing)) {
                if (risky) LinkWarnings(link)
                Surface(shape = BoxShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(BoxPadding)) {
                        Text(
                            text = stringResource(R.string.reader_link_address),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(LineSpacing / 2))
                        Text(link.shownUrl, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = if (risky) ButtonDefaults.textButtonColors() else quietButton(),
            ) { Text(stringResource(R.string.cancel)) }
            if (link.canOpen) {
                TextButton(
                    onClick = {
                        openLink(context, link.url)
                        onDismiss()
                    },
                    colors = if (risky) quietButton() else ButtonDefaults.textButtonColors(),
                ) { Text(stringResource(if (risky) R.string.reader_link_open_anyway else R.string.reader_link_open)) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                copyLink(context, link.url)
                onDismiss()
            }) { Text(stringResource(R.string.reader_link_copy)) }
        },
    )
}

@Composable
private fun quietButton() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun LinkWarnings(link: PendingLink) {
    Surface(
        shape = BoxShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(BoxPadding), verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LineSpacing)) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(WarningIconSize))
                Text(stringResource(R.string.reader_link_check), style = MaterialTheme.typography.titleSmall)
            }
            if (!link.canOpen) {
                Text(stringResource(R.string.reader_link_blocked), style = MaterialTheme.typography.bodyMedium)
            } else {
                link.warnings.forEach { warning ->
                    Text(stringResource(warning.labelRes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

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
