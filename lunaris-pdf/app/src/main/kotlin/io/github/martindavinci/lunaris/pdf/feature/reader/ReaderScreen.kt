package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState

/** The grey around the pages; the same in viewer.css, so nothing flashes while the page loads. */
internal val PageBackground = Color(0xFF2B2D30)

/** Keeps the button clear of the side scroller, which is 44 dp wide. */
private val TopButtonEndInset = 52.dp
/** Neutral and translucent: the page stays the only colour on screen, and shows through. */
private val TopButtonBackground = Color(0x8C3C3C3C)
private val TopButtonBottomInset = 72.dp
private val TopButtonBottomInsetNoBars = 16.dp
private val MessagePadding = 24.dp
private val MessageSpacing = 12.dp

/** Stateless Reader UI. [document] is a slot, so a preview needs no WebView. */
@Composable
internal fun ReaderScreen(
    state: ReaderUiState,
    onAction: (ReaderAction) -> Unit,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onSaveCopy: () -> Unit,
    document: @Composable () -> Unit,
) {
    var showGoToPage by rememberSaveable { mutableStateOf(false) }
    var showOutline by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(PageBackground).systemBarsPadding().displayCutoutPadding()) {
        document()

        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        state.error?.let { ReaderErrorMessage(onBack, Modifier.align(Alignment.Center)) }

        val barsVisible = state.chromeVisible && state.error == null
        AnimatedVisibility(visible = barsVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            if (state.search != null) {
                ReaderSearchBar(state.search, onAction)
            } else {
                ReaderTopBar(
                    name = state.name,
                    night = state.night,
                    onBack = onBack,
                    canPrint = state.canPrint,
                    onAction = onAction,
                    onGoToPage = { showGoToPage = true },
                    onPrint = onPrint,
                    onSaveCopy = onSaveCopy,
                )
            }
        }
        val readingAloud = state.readAloud != ReadAloudState.Idle
        val bottomBarVisible = readingAloud || (barsVisible && state.pages > 0)
        AnimatedVisibility(visible = bottomBarVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            if (readingAloud) {
                ReadAloudBar(state.readAloud, onAction)
            } else {
                ReaderBottomBar(
                    page = state.page,
                    pages = state.pages,
                    hasOutline = state.outline.isNotEmpty(),
                    onGoToPage = { showGoToPage = true },
                    onOutline = { showOutline = true },
                )
            }
        }
        if (state.showTopButton && state.error == null) {
            SmallFloatingActionButton(
                onClick = { onAction(ReaderAction.BackToTop) },
                containerColor = TopButtonBackground,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(
                    end = TopButtonEndInset,
                    bottom = if (bottomBarVisible) TopButtonBottomInset else TopButtonBottomInsetNoBars,
                ),
            ) {
                Icon(Icons.Filled.VerticalAlignTop, contentDescription = stringResource(R.string.reader_back_to_top))
            }
        }
        if (showOutline) {
            OutlinePanel(
                items = state.outline,
                onSelect = { page ->
                    showOutline = false
                    onAction(ReaderAction.GoToPage(page))
                },
                onDismiss = { showOutline = false },
            )
        }
    }

    state.passwordPrompt?.let { prompt ->
        PasswordDialog(
            prompt = prompt,
            onSubmit = { onAction(ReaderAction.SubmitPassword(it)) },
            onCancel = onBack,
        )
    }
    state.pendingLink?.let { link ->
        LinkDialog(link, onDismiss = { onAction(ReaderAction.DismissLink) })
    }
    if (showGoToPage) {
        GoToPageDialog(
            pages = state.pages,
            onGo = {
                showGoToPage = false
                onAction(ReaderAction.GoToPage(it))
            },
            onDismiss = { showGoToPage = false },
        )
    }
}

@Composable
private fun ReaderErrorMessage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MessagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MessageSpacing),
    ) {
        Text(
            text = stringResource(R.string.reader_cannot_open),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.reader_go_back)) }
    }
}
