package tools.lunaris.texteditor.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import tools.lunaris.texteditor.R

/** Opens this app's folder in the public repository in the browser; the app itself never goes online. */
internal fun openSourceCode(context: Context) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.source_url)))
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.set_no_browser, Toast.LENGTH_SHORT).show()
    }
}
