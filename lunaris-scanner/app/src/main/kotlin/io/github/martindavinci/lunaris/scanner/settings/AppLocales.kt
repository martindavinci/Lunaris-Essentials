package io.github.martindavinci.lunaris.scanner.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/**
 * Reads and applies the in-app UI language. On Android 13+ the system stores the choice (and
 * shows it in the app's system settings); below that it lives in a small preferences file and
 * [wrap] applies it to each activity.
 */
object AppLocales {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    /** The chosen language, or [AppLanguage.SYSTEM]. */
    fun current(context: Context): String = AppLanguage.normalize(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString(KEY_TAG, null)
        }
    )

    /** Switches the UI to [tag] ([AppLanguage.SYSTEM] to follow the system) and redraws [activity]. */
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system restarts the activity in the new language.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit { putString(KEY_TAG, tag) }
            activity.recreate()
        }
    }

    /** Below Android 13, returns [base] with its resources in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = AppLanguage.normalize(prefs(base).getString(KEY_TAG, null))
        if (tag == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
