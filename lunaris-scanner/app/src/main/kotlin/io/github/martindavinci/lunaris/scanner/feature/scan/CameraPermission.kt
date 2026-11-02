package io.github.martindavinci.lunaris.scanner.feature.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PACKAGE_SCHEME = "package"

internal fun Context.cameraPermissionState(): ScanAction.PermissionChecked {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    val showRationale = findActivity()?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false
    return ScanAction.PermissionChecked(granted, showRationale)
}

internal fun Context.openAppSettings() {
    val uri = Uri.fromParts(PACKAGE_SCHEME, packageName, null)
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
