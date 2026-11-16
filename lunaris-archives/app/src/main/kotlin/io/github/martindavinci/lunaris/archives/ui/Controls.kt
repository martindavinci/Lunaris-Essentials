package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisOnDeepBlueVariant
import io.github.martindavinci.lunaris.archives.ui.theme.accentText

private val FilledPadding = PaddingValues(horizontal = 24.dp)
private val OutlinedPadding = PaddingValues(horizontal = 20.dp)
private val ButtonIconSize = 18.dp
private val ButtonIconSpacing = 8.dp
private val BorderWidth = 1.dp
private val CheckOffset = (-14).dp
private val CheckRowMinHeight = 48.dp
private val ChoiceMinHeight = 56.dp
private val ChoiceShape = RoundedCornerShape(16.dp)
private val ChoiceEndPadding = 16.dp

/** The filled button of the family: deep blue in both themes. */
@Composable
fun DeepButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        contentPadding = FilledPadding,
    ) { ButtonContent(text, icon) }
}

@Composable
fun SoftOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentText),
        border = BorderStroke(BorderWidth, colors.outline),
        contentPadding = OutlinedPadding,
    ) { ButtonContent(text, icon) }
}

@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.accentText)) {
        Text(text)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ButtonIconSpacing)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Text(text, maxLines = 1)
    }
}

/** A checkbox with its text; the whole row toggles. Shifted so the box lines up with the text of a field above it. */
@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = CheckOffset)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .heightIn(min = CheckRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One of a few choices, with a second line when the choice has a consequence to show ("notes (1).md"). */
@Composable
fun ChoiceRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ChoiceShape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = ChoiceMinHeight)
            .padding(end = ChoiceEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(CheckRowMinHeight), Alignment.Center) {
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accentText))
        }
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (selected) LunarisOnDeepBlueVariant else colors.onSurfaceVariant)
            }
        }
    }
}
