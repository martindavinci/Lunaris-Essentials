package io.github.martindavinci.lunaris.scanner.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.encode.CodeEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val PREVIEW_DEBOUNCE_MS = 250L

/** Side of saved and shared images: several pixels per module even for the densest QR code. */
internal const val EXPORT_SIZE_PX = 1024

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val renderer: CodeImageRenderer,
    private val files: CodeImageFiles,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState().withForm(CreateForm()))
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { renderPreviews() }
    }

    fun onAction(action: CreateAction) {
        when (action) {
            is CreateAction.SelectType -> edit { it.copy(type = action.type) }
            is CreateAction.SelectFormat -> edit { it.withFormat(action.format) }
            is CreateAction.EditForm -> edit { action.form }
            is CreateAction.PreviewSized -> _state.update { it.copy(previewSizePx = action.sizePx) }
            is CreateAction.SaveTo -> save(action.uri)
            CreateAction.Share -> share()
            CreateAction.ShareHandled -> _state.update { it.copy(shareUri = null) }
            CreateAction.Copied -> show(CreateMessage.COPIED)
            CreateAction.MessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun edit(change: (CreateForm) -> CreateForm) {
        _state.update { it.withForm(change(it.form)) }
    }

    /** A new edit cancels the wait and any encoding under way: only the last one reaches the screen. */
    private suspend fun renderPreviews() {
        _state.map { PreviewRequest(it.result as? PayloadResult.Ready, it.previewSizePx) }
            .distinctUntilChanged()
            .collectLatest { (ready, sizePx) ->
                if (ready == null || sizePx <= 0) {
                    _state.update { it.copy(preview = null, encodeFailed = false) }
                } else {
                    delay(PREVIEW_DEBOUNCE_MS)
                    val image = renderer.render(ready.payload, ready.format, sizePx)
                    _state.update { it.copy(preview = image, encodeFailed = image == null) }
                }
            }
    }

    private fun save(uri: String) {
        // Nothing to save when the form was lost with the process while the file picker was open.
        val ready = _state.value.result as? PayloadResult.Ready
        viewModelScope.launch {
            val image = ready?.let { renderer.render(it.payload, it.format, EXPORT_SIZE_PX) }
            val saved = image != null && files.savePng(uri, image)
            if (!saved) files.discard(uri)
            show(if (saved) CreateMessage.SAVED else CreateMessage.SAVE_FAILED)
        }
    }

    private fun share() {
        val ready = _state.value.result as? PayloadResult.Ready ?: return
        viewModelScope.launch {
            val image = renderer.render(ready.payload, ready.format, EXPORT_SIZE_PX)
            val uri = image?.let { files.cachePngForSharing(it) }
            if (uri != null) {
                _state.update { it.copy(shareUri = uri) }
            } else {
                show(CreateMessage.SHARE_FAILED)
            }
        }
    }

    private fun show(message: CreateMessage) {
        _state.update { it.copy(message = message) }
    }

    private fun CreateUiState.withForm(form: CreateForm) = copy(
        form = form,
        formats = CreatePayload.formatsFor(form.type, CodeEncoder.encodableFormats),
        result = CreatePayload.evaluate(form),
    )

    private data class PreviewRequest(val ready: PayloadResult.Ready?, val sizePx: Int)
}
