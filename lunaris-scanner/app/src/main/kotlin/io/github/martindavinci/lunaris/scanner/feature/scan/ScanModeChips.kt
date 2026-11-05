package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val ChipSpacing = 8.dp
private val BarPadding = 8.dp
private const val BAR_ALPHA = 0.85f

/** Over the live camera: the bar gives the chips a surface that any picture leaves readable. */
@Composable
internal fun ScanModeChips(mode: ScanMode, enabled: Boolean, onSelect: (ScanMode) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = BAR_ALPHA)) {
        Row(
            modifier = Modifier.padding(horizontal = BarPadding),
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        ) {
            ScanMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    ScanMode.CODE -> R.string.scan_mode_code
                                    ScanMode.DOCUMENT -> R.string.scan_mode_document
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}
