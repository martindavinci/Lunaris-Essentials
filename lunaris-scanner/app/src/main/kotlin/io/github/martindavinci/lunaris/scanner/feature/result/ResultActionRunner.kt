package io.github.martindavinci.lunaris.scanner.feature.result

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.share.markSensitive

/**
 * Carries out a [ResultAction] and returns the message to show, if any.
 *
 * The Wi-Fi sheet goes through [launchForResult]: it needs to know the calling app, which the
 * system only reports to an activity started for a result.
 */
internal class ResultActionRunner(
    private val context: Context,
    private val launchForResult: (Intent) -> Unit,
) {
    @StringRes
    fun run(action: ResultAction): Int? = when (action) {
        is ResultAction.OpenUrl -> start(ResultIntents.openUrl(action.url))
        is ResultAction.ConnectWifi -> connectWifi(action.wifi)
        is ResultAction.CopyPassword -> copy(action.password, sensitive = true)
        is ResultAction.AddContact -> start(ResultIntents.addContact(action.contact))
        is ResultAction.SendEmail -> start(ResultIntents.sendEmail(action.email))
        is ResultAction.Dial -> start(ResultIntents.dial(action.number))
        is ResultAction.SendSms -> start(ResultIntents.sendSms(action.sms))
        is ResultAction.ViewMap -> start(ResultIntents.viewMap(action.geo))
        is ResultAction.AddEvent -> start(ResultIntents.addEvent(action.event))
        is ResultAction.WebSearch -> start(ResultIntents.webSearch(action.query))
        is ResultAction.Copy -> copy(action.text, action.sensitive)
        is ResultAction.Share -> start(ResultIntents.share(action.text))
    }

    @StringRes
    private fun connectWifi(wifi: ParsedContent.Wifi): Int? {
        val addNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && WifiPolicy.canAddDirectly(wifi)) {
            ResultIntents.addWifiNetwork(wifi)
        } else {
            null
        }
        if (addNetwork != null && launchedForResult(addNetwork)) return null
        return joinByHand(wifi)
    }

    private fun launchedForResult(intent: Intent): Boolean = try {
        launchForResult(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    @StringRes
    private fun joinByHand(wifi: ParsedContent.Wifi): Int? {
        val password = wifi.secret
        if (password != null) copy(password, sensitive = true)
        val failure = start(ResultIntents.wifiSettings())
        return failure ?: R.string.result_wifi_join_by_hand.takeIf { password != null }
    }

    @StringRes
    private fun start(intent: Intent): Int? = try {
        context.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        R.string.result_no_app
    }

    /** From Android 13 the system confirms a copy by itself, so a message would only repeat it. */
    @StringRes
    private fun copy(text: String, sensitive: Boolean): Int? {
        val clip = ClipData.newPlainText(context.getString(R.string.result_clip_label), text)
        if (sensitive) clip.markSensitive()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) null else R.string.result_copied
    }
}
