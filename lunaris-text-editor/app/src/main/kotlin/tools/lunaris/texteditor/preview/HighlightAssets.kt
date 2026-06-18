package tools.lunaris.texteditor.preview

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the bundled (offline) highlight.js library + light/dark themes, read once and cached. */
@Singleton
class HighlightAssets @Inject constructor(@ApplicationContext private val context: Context) {
    val js: String by lazy { read("highlight/highlight.min.js") }
    val lightCss: String by lazy { read("highlight/github.min.css") }
    val darkCss: String by lazy { read("highlight/github-dark.min.css") }

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
