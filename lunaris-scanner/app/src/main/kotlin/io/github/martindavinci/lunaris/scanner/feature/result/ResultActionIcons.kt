package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

internal val ResultAction.icon: ImageVector
    get() = when (this) {
        is ResultAction.OpenUrl -> Icons.AutoMirrored.Filled.OpenInNew
        is ResultAction.ConnectWifi -> Icons.Filled.Wifi
        is ResultAction.CopyPassword -> Icons.Filled.VpnKey
        is ResultAction.AddContact -> Icons.Filled.PersonAdd
        is ResultAction.SendEmail -> Icons.Filled.Email
        is ResultAction.Dial -> Icons.Filled.Dialpad
        is ResultAction.SendSms -> Icons.Filled.Sms
        is ResultAction.ViewMap -> Icons.Filled.Place
        is ResultAction.AddEvent -> Icons.Filled.Event
        is ResultAction.WebSearch -> Icons.Filled.Search
        is ResultAction.Copy -> Icons.Filled.ContentCopy
        is ResultAction.Share -> Icons.Filled.Share
    }
