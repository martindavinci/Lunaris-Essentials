package io.github.martindavinci.lunaris.archives

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Runs in the isolated :engine process too: keep it empty (no injection, no start-up work).
@HiltAndroidApp
class LunarisArchivesApp : Application()
