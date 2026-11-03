package io.github.martindavinci.lunaris.pdf.model

/** The file being read. [page] and [scale] are where reading resumes; [canWrite] tells if it can be saved over. */
data class OpenDocument(
    val uri: String,
    val name: String,
    val canWrite: Boolean = false,
    val page: Int = 1,
    val scale: String = DEFAULT_SCALE,
) {
    companion object {
        /** pdf.js fits the page to the width of the screen. */
        const val DEFAULT_SCALE = "page-width"
    }
}
