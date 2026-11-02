package io.github.martindavinci.lunaris.scanner.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

private val FieldSpacing = 8.dp
private val SwitchRowPadding = 8.dp
private const val LONG_TEXT_MIN_LINES = 3

/** The input fields of the selected type. */
@Composable
internal fun CreateFields(form: CreateForm, onChange: (CreateForm) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        when (form.type) {
            CreateType.TEXT -> LongField(form.text, R.string.create_field_text) { onChange(form.copy(text = it)) }
            CreateType.URL -> Field(form.url, R.string.create_field_url, KeyboardType.Uri) { onChange(form.copy(url = it)) }
            CreateType.WIFI -> WifiForm(form.wifi) { onChange(form.copy(wifi = it)) }
            CreateType.CONTACT -> ContactForm(form.contact) { onChange(form.copy(contact = it)) }
            CreateType.EMAIL -> EmailForm(form.email) { onChange(form.copy(email = it)) }
            CreateType.PHONE -> Field(form.phone, R.string.create_field_phone, KeyboardType.Phone) { onChange(form.copy(phone = it)) }
            CreateType.SMS -> SmsForm(form.sms) { onChange(form.copy(sms = it)) }
            CreateType.BARCODE -> Field(form.barcode, R.string.create_field_barcode) { onChange(form.copy(barcode = it)) }
        }
    }
}

@Composable
private fun WifiForm(fields: WifiFields, onChange: (WifiFields) -> Unit) {
    Field(fields.ssid, R.string.create_field_ssid) { onChange(fields.copy(ssid = it)) }
    ChoiceChips(
        options = WifiSecurity.entries,
        selected = fields.security,
        label = { labelOf(it) },
        onSelect = { onChange(fields.copy(security = it)) },
    )
    if (fields.security != WifiSecurity.NONE) {
        Field(fields.password, R.string.create_field_password, KeyboardType.Password) { onChange(fields.copy(password = it)) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(fields.copy(hidden = !fields.hidden)) }
            .padding(vertical = SwitchRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.create_field_hidden),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = fields.hidden, onCheckedChange = { onChange(fields.copy(hidden = it)) })
    }
}

@Composable
private fun ContactForm(fields: ContactFields, onChange: (ContactFields) -> Unit) {
    Field(fields.name, R.string.create_field_name) { onChange(fields.copy(name = it)) }
    Field(fields.organization, R.string.create_field_organization) { onChange(fields.copy(organization = it)) }
    Field(fields.title, R.string.create_field_job_title) { onChange(fields.copy(title = it)) }
    Field(fields.phone, R.string.create_field_phone, KeyboardType.Phone) { onChange(fields.copy(phone = it)) }
    Field(fields.email, R.string.create_field_email, KeyboardType.Email) { onChange(fields.copy(email = it)) }
    Field(fields.address, R.string.create_field_address) { onChange(fields.copy(address = it)) }
    Field(fields.website, R.string.create_field_website, KeyboardType.Uri) { onChange(fields.copy(website = it)) }
    LongField(fields.note, R.string.create_field_note) { onChange(fields.copy(note = it)) }
}

@Composable
private fun EmailForm(fields: EmailFields, onChange: (EmailFields) -> Unit) {
    Field(fields.to, R.string.create_field_to, KeyboardType.Email) { onChange(fields.copy(to = it)) }
    Field(fields.subject, R.string.create_field_subject) { onChange(fields.copy(subject = it)) }
    LongField(fields.body, R.string.create_field_message) { onChange(fields.copy(body = it)) }
}

@Composable
private fun SmsForm(fields: SmsFields, onChange: (SmsFields) -> Unit) {
    Field(fields.number, R.string.create_field_phone, KeyboardType.Phone) { onChange(fields.copy(number = it)) }
    LongField(fields.body, R.string.create_field_message) { onChange(fields.copy(body = it)) }
}

@Composable
private fun Field(
    value: String,
    @StringRes label: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LongField(value: String, @StringRes label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        minLines = LONG_TEXT_MIN_LINES,
        modifier = Modifier.fillMaxWidth(),
    )
}
