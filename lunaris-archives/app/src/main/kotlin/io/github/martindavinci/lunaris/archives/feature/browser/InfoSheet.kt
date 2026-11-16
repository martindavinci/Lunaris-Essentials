package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.ui.BottomPanel
import io.github.martindavinci.lunaris.archives.ui.DetailRow
import io.github.martindavinci.lunaris.archives.ui.DetailText
import io.github.martindavinci.lunaris.archives.ui.DetailsBox
import io.github.martindavinci.lunaris.archives.ui.FormatBadge
import io.github.martindavinci.lunaris.archives.ui.PanelHeader
import io.github.martindavinci.lunaris.archives.ui.percentText
import io.github.martindavinci.lunaris.archives.ui.sizeText

private val CommentMargin = 16.dp
private val CommentPadding = 12.dp
private val CommentShape = RoundedCornerShape(20.dp)
private const val COMMENT_LINES = 6

/** Frame 16: the archive as a whole. */
@Composable
internal fun InfoSheet(header: ArchiveHeader, onAction: (BrowserAction) -> Unit) {
    BottomPanel(onDismiss = { onAction(BrowserAction.DismissSheet) }) {
        PanelHeader(stringResource(R.string.info_title), header.name)
        DetailsBox {
            DetailRow(stringResource(R.string.info_format)) { FormatBadge(header.formatLabel) }
            DetailRow(stringResource(R.string.info_files)) { DetailText(header.fileCount.toString()) }
            DetailRow(stringResource(R.string.info_folders)) { DetailText(header.folderCount.toString()) }
            DetailRow(stringResource(R.string.entry_size)) { DetailText(sizeText(header.sizeBytes)) }
            header.packedBytes?.let { packed ->
                val ratio = if (header.sizeBytes > 0) packed.toFloat() / header.sizeBytes else 1f
                DetailRow(stringResource(R.string.entry_packed)) {
                    DetailText(stringResource(R.string.info_packed_value, sizeText(packed), percentText(ratio)))
                }
            }
            DetailRow(stringResource(R.string.entry_encrypted)) { DetailText(stringResource(encryptionText(header.encryption))) }
            header.createdBy?.let { host -> DetailRow(stringResource(R.string.info_created_by)) { DetailText(stringResource(hostText(host))) } }
        }
        header.comment?.takeIf { it.isNotBlank() }?.let { Comment(it) }
    }
}

@Composable
private fun Comment(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CommentMargin)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CommentShape).padding(CommentPadding),
    ) {
        Text(stringResource(R.string.info_comment), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else COMMENT_LINES, overflow = TextOverflow.Ellipsis,
        )
        if (!expanded) TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.info_more)) }
    }
}

private fun encryptionText(encryption: Encryption): Int = when (encryption) {
    Encryption.NONE -> R.string.no
    Encryption.SOME_ENTRIES -> R.string.info_encrypted_some
    Encryption.ALL_ENTRIES -> R.string.info_encrypted_all
    Encryption.HEADERS -> R.string.info_encrypted_names
}

private fun hostText(host: HostOs): Int = when (host) {
    HostOs.WINDOWS -> R.string.info_os_windows
    HostOs.UNIX -> R.string.info_os_unix
    HostOs.MACOS -> R.string.info_os_mac
    HostOs.MSDOS -> R.string.info_os_msdos
    HostOs.OTHER -> R.string.info_unknown
}
