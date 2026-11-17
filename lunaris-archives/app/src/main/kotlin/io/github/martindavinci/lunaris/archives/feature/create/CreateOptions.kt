package io.github.martindavinci.lunaris.archives.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.create.CompressionLevel
import io.github.martindavinci.lunaris.archives.create.FormatChoice
import io.github.martindavinci.lunaris.archives.create.Packing
import io.github.martindavinci.lunaris.archives.create.archiveFormat
import io.github.martindavinci.lunaris.archives.ui.PasswordField

private val OptionsPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val SectionTop = 24.dp
private val SmallGap = 8.dp
private val MediumGap = 12.dp
private val LargeGap = 20.dp
private val SwitchRowMinHeight = 56.dp
private val MeterInset = 4.dp
private val MeterSpacing = 10.dp
private val SegmentHeight = 4.dp
private val SegmentGap = 4.dp
private val SegmentShape = RoundedCornerShape(2.dp)
private val NoteIconSize = 18.dp
private val CreateMinHeight = 56.dp
private val CreateShape = RoundedCornerShape(16.dp)
private val CreateIconSize = 22.dp
private val CreateIconGap = 10.dp
private const val STRENGTH_STEPS = 4
private const val WEAK = 1

/** Frame 14: name, compression, password, and the button at the end of the page. */
@Composable
internal fun CreateOptions(state: CreateUiState, password: TextFieldState, confirm: TextFieldState, onAction: (CreateAction) -> Unit, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(OptionsPadding).navigationBarsPadding()) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(CreateAction.SetName(it)) },
            label = { Text(stringResource(R.string.create_name)) },
            suffix = { Text(state.format.suffix, color = colors.onSurface) },
            isError = state.nameError,
            supportingText = if (state.nameError) { { Text(stringResource(R.string.create_name_invalid)) } } else null,
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        OptionLabel(stringResource(R.string.create_format))
        ChoiceRow(FormatChoice.entries, state.formatChoice, { stringResource(it.label()) }) { onAction(CreateAction.SetFormat(it)) }
        OptionLabel(stringResource(R.string.create_compression))
        if (state.format.usesLevel) {
            ChoiceRow(CompressionLevel.entries, state.level, { stringResource(it.label()) }) { onAction(CreateAction.SetLevel(it)) }
        } else {
            ChoiceRow(Packing.entries, state.packing, { archiveFormat(FormatChoice.TAR, it).suffix }) { onAction(CreateAction.SetPacking(it)) }
        }
        if (!state.format.takesPassword) {
            Text(
                stringResource(R.string.create_password_zip_only),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = MediumGap).heightIn(min = SwitchRowMinHeight).wrapContentHeight(Alignment.CenterVertically),
            )
        } else Row(
            Modifier.fillMaxWidth().padding(top = MediumGap).toggleable(state.protect, role = Role.Switch) { onAction(CreateAction.SetProtect(it)) }.heightIn(min = SwitchRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.create_protect), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.protect, onCheckedChange = null)
        }
        if (state.protect) {
            PasswordField(password, stringResource(R.string.password_label), Modifier.fillMaxWidth().padding(top = MediumGap))
            StrengthMeter(state.strength)
            PasswordField(
                state = confirm,
                label = stringResource(R.string.create_confirm),
                modifier = Modifier.fillMaxWidth().padding(top = LargeGap),
                isError = state.mismatch,
                supporting = if (state.mismatch) stringResource(R.string.create_mismatch) else null,
                imeAction = ImeAction.Done,
            )
            Row(Modifier.padding(start = MeterInset, end = MeterInset, top = MediumGap), horizontalArrangement = Arrangement.spacedBy(MeterSpacing)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(NoteIconSize))
                Text(
                    text = stringResource(R.string.create_aes_note) + " " + stringResource(R.string.create_names_visible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onCreate,
            enabled = state.canCreate,
            shape = CreateShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
            modifier = Modifier.fillMaxWidth().padding(top = LargeGap).heightIn(min = CreateMinHeight),
        ) {
            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(CreateIconSize))
            Text(stringResource(state.formatChoice.buttonLabel()), modifier = Modifier.padding(start = CreateIconGap))
        }
        Text(
            text = stringResource(R.string.create_save_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = SmallGap),
        )
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = SectionTop, bottom = SmallGap))
}

/** One row of segmented choices, like Compression. */
@Composable
private fun <T> ChoiceRow(choices: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { i, choice ->
            SegmentedButton(
                selected = selected == choice,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = choices.size),
            ) { Text(label(choice), maxLines = 1) }
        }
    }
}

private fun FormatChoice.label(): Int = when (this) {
    FormatChoice.ZIP -> R.string.create_format_zip
    FormatChoice.SEVEN_Z -> R.string.create_format_7z
    FormatChoice.TAR -> R.string.create_format_tar
}

private fun FormatChoice.buttonLabel(): Int = when (this) {
    FormatChoice.ZIP -> R.string.create_button
    FormatChoice.SEVEN_Z -> R.string.create_button_7z
    FormatChoice.TAR -> R.string.create_button_tar
}

private fun CompressionLevel.label(): Int = when (this) {
    CompressionLevel.STORE -> R.string.create_store
    CompressionLevel.NORMAL -> R.string.create_normal
    CompressionLevel.MAXIMUM -> R.string.create_maximum
}

/** Four segments and a word. Nothing while the field is empty. */
@Composable
internal fun StrengthMeter(level: Int) {
    val colors = MaterialTheme.colorScheme
    if (level <= 0) return
    val label = when {
        level <= WEAK -> R.string.create_strength_weak
        level == WEAK + 1 -> R.string.create_strength_fair
        level == WEAK + 2 -> R.string.create_strength_good
        else -> R.string.create_strength_strong
    }
    Row(Modifier.fillMaxWidth().padding(start = MeterInset, end = MeterInset, top = SmallGap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeterSpacing)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            repeat(STRENGTH_STEPS) { step ->
                Box(Modifier.weight(1f).height(SegmentHeight).background(if (step < level) colors.primary else colors.surfaceVariant, SegmentShape))
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
