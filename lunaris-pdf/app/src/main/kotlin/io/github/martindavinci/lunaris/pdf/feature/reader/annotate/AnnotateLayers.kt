package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderUiState
import kotlinx.coroutines.delay

private const val HINT_MS = 3000L
private const val SCRIM_ALPHA = 0.55f
private val HintColor = Color(0xD91E2127)
private val HintShape = RoundedCornerShape(16.dp)
private val HintPaddingHorizontal = 14.dp
private val HintPaddingVertical = 8.dp
private val HintMargin = 12.dp
private val SavingShape = RoundedCornerShape(16.dp)
private val SavingWidthPadding = 32.dp
private val SavingPadding = 20.dp
private val SavingSpacing = 12.dp

/** Above the document and its bars: the hints, the sheets, and the wait while saving. */
@Composable
internal fun BoxScope.AnnotateLayers(state: ReaderUiState, bottomSpace: Dp, onAction: (ReaderAction) -> Unit, onSaveCopy: () -> Unit) {
    state.annotation?.let { annotation ->
        ScrollHint(annotation.showScrollHint, bottomSpace, onDone = { onAction(EditAction.HideScrollHint) })
        NoTextHint(annotation.noTextHints, bottomSpace)
        annotation.note?.let { NoteSheet(it, onAction) }
        annotation.signatures?.let { SignaturesPanel(it, onAction) }
    }
    state.saveSheet?.let { SaveAnnotationsSheet(it, state.canWrite, onAction, onSaveCopy) }
    state.exportProgress?.let { Saving(it) }
}

@Composable
private fun BoxScope.ScrollHint(visible: Boolean, bottomSpace: Dp, onDone: () -> Unit) {
    if (visible) {
        LaunchedEffect(Unit) {
            delay(HINT_MS)
            onDone()
        }
    }
    Hint(visible, stringResource(R.string.annotate_hint_scroll), bottomSpace)
}

@Composable
private fun BoxScope.NoTextHint(count: Int, bottomSpace: Dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(count) {
        if (count > 0) {
            visible = true
            delay(HINT_MS)
            visible = false
        }
    }
    Hint(visible, stringResource(R.string.annotate_hint_no_text), bottomSpace)
}

@Composable
private fun BoxScope.Hint(visible: Boolean, text: String, bottomSpace: Dp) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace + HintMargin, start = HintMargin, end = HintMargin),
    ) {
        Surface(shape = HintShape, color = HintColor, contentColor = Color.White) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = HintPaddingHorizontal, vertical = HintPaddingVertical),
            )
        }
    }
}

/** A file can take a while, and the document must not change under it: nothing else takes a touch. */
@Composable
private fun BoxScope.Saving(progress: Float) {
    BackHandler {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = SavingShape, modifier = Modifier.fillMaxWidth().padding(horizontal = SavingWidthPadding)) {
            Column(Modifier.padding(SavingPadding), verticalArrangement = Arrangement.spacedBy(SavingSpacing)) {
                Text(stringResource(R.string.annotate_saving), style = MaterialTheme.typography.titleSmall)
                if (progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
