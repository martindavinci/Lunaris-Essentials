package io.github.martindavinci.lunaris.archives.feature.create

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** The system "Save as", with the MIME type chosen at launch: one launcher serves every format. */
class SaveAsContract : ActivityResultContract<SaveAsContract.Input, Uri?>() {
    data class Input(val name: String, val mime: String)

    override fun createIntent(context: Context, input: Input): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mime)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent.takeIf { resultCode == android.app.Activity.RESULT_OK }?.data
}
