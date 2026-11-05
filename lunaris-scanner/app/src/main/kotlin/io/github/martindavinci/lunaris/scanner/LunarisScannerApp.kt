package io.github.martindavinci.lunaris.scanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.martindavinci.lunaris.scanner.document.clearPhotosLeftBehind

@HiltAndroidApp
class LunarisScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        clearPhotosLeftBehind(cacheDir)
    }
}
