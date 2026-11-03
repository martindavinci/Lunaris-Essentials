package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import kotlin.math.roundToInt

/** The dark around the pages; the same in viewer.css, so nothing flashes while the page loads. */
internal val PageBackground = Color(0xFF15171C)

private val MessagePadding = 24.dp
private val MessageSpacing = 12.dp

/**
 * Stateless Reader UI. [document] is a slot, so a preview needs no WebView. It fills the screen and
 * keeps its size when the bars come and go, so that the pages never move under the reader.
 */
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
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(topHeight, bottomHeight) {
        if (topHeight > 0 && bottomHeight > 0) {
            onAction(
                ReaderAction.SetBarSpace(
                    top = (topHeight / density.density).roundToInt(),
                    bottom = (bottomHeight / density.density).roundToInt(),
                ),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(PageBackground).displayCutoutPadding()) {
        document()

        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        state.error?.let { ReaderErrorMessage(onBack, Modifier.align(Alignment.Center)) }

        val barsVisible = (state.chromeVisible || state.search != null) && state.error == null
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (state.search != null) {
                ReaderSearchBar(state.search, onAction)
            } else {
                Box(Modifier.onSizeChanged { topHeight = it.height }) {
                    ReaderTopBar(
                        name = state.name,
                        chapter = state.chapter,
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
        }
        val bottomVisible = state.search != null || (barsVisible && state.pages > 0)
        AnimatedVisibility(
            visible = bottomVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            when {
                state.search != null -> ReaderSearchNav(state.search, onAction)
                else -> Box(Modifier.onSizeChanged { bottomHeight = it.height }) {
                    ReaderBottomBar(
                        page = state.page,
                        pages = state.pages,
                        night = state.night,
                        onAction = onAction,
                        onGoToPage = { showGoToPage = true },
                        onOutline = { showOutline = true },
                    )
                }
            }
        }
        if (state.error == null) {
            ReaderOverlays(
                state = state,
                bottomSpace = with(density) { if (barsVisible) bottomHeight.toDp() else 0.dp },
                onAction = onAction,
            )
        }
        if (showOutline) {
            OutlinePanel(
                items = state.outline,
                page = state.page,
                pages = state.pages,
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
            name = state.name,
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
