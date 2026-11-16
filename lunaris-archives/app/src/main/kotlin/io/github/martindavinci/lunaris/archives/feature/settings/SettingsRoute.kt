package io.github.martindavinci.lunaris.archives.feature.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.settings.AppLocales
import kotlinx.coroutines.launch

/** Settings, with the language dialog and the licences shown in place. */
@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A language change restarts the activity, so reading it once is enough.
    val language = remember { AppLocales.current(context) }
    var showLicences by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var chosenLanguage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Below Android 13 the choice restarts the activity at once. With the dialog still open, its window
    // would keep the focus while it goes away, and the app would stop answering: so the dialog closes first.
    LaunchedEffect(chosenLanguage) {
        chosenLanguage?.let { tag ->
            withFrameNanos { }
            (context as? Activity)?.let { AppLocales.set(it, tag) }
        }
    }

    LaunchedEffect(state.notice) {
        if (state.notice != SettingsNotice.Cleared) return@LaunchedEffect
        viewModel.onAction(SettingsAction.NoticeShown)
        snackbar.showSnackbar(context.getString(R.string.settings_cleared))
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (showLicences) {
            BackHandler { showLicences = false }
            LicensesScreen(onBack = { showLicences = false })
        } else {
            SettingsContent(
                state = state,
                language = languageLabel(language),
                onAction = viewModel::onAction,
                onLanguage = { showLanguage = true },
                onLicences = { showLicences = true },
                onSource = {
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.source_url)))
                    try {
                        context.startActivity(view)
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_no_browser)) }
                    }
                },
                onBack = onBack,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (showLanguage) {
        LanguageDialog(
            current = language,
            onSelect = { tag ->
                showLanguage = false
                chosenLanguage = tag
            },
            onDismiss = { showLanguage = false },
        )
    }
}
