package io.github.martindavinci.lunaris.scanner.feature.create

import io.github.martindavinci.lunaris.scanner.model.CodeFormat

data class CreateUiState(
    val form: CreateForm = CreateForm(),
    /** The choices of the format picker. A single one means the format is locked. */
    val formats: List<CodeFormat> = emptyList(),
    val result: PayloadResult = PayloadResult.Incomplete,
    val preview: CodeImage? = null,
    /** Width of the preview area on screen, so that one image pixel is one screen pixel. */
    val previewSizePx: Int = 0,
    val encodeFailed: Boolean = false,
    val message: CreateMessage? = null,
    /** A content URI waiting to be handed to the share sheet. */
    val shareUri: String? = null,
)

enum class CreateMessage { SAVED, SAVE_FAILED, SHARE_FAILED, COPIED }

sealed interface CreateAction {
    data class SelectType(val type: CreateType) : CreateAction

    data class SelectFormat(val format: CodeFormat) : CreateAction

    data class EditForm(val form: CreateForm) : CreateAction

    data class PreviewSized(val sizePx: Int) : CreateAction

    data class SaveTo(val uri: String) : CreateAction

    data object Share : CreateAction

    data object ShareHandled : CreateAction

    data object Copied : CreateAction

    data object MessageShown : CreateAction
}
